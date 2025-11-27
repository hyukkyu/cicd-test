import json
import os
import time
from decimal import Decimal

import boto3
import pymysql

rekognition = boto3.client('rekognition')
sns = boto3.client('sns')

MEDIA_CONFIDENCE = float(os.getenv('MEDIA_CONFIDENCE', '0.7')) * 100
ADMIN_ALERT_TOPIC = os.getenv('ADMIN_ALERT_TOPIC')
VIDEO_EXTENSIONS = ('.mp4', '.mov', '.avi', '.mkv') #ㅁㄴㅇㄹ

DB_HOST = os.environ['DB_HOST']
DB_NAME = os.environ['DB_NAME']
DB_USER = os.environ['DB_USER']
DB_PASSWORD = os.environ['DB_PASSWORD']
DB_PORT = int(os.getenv('DB_PORT', '3306'))

connection = None


def lambda_handler(event, _context):
    """Process SNS->SQS moderation messages."""
    responses = []
    for record in event.get('Records', []):
        try:
            payload = _extract_payload(record)
            if not payload:
                continue
            result = _moderate_and_persist(payload)
            responses.append(result)
        except Exception as exc:
            responses.append({'status': 'ERROR', 'error': str(exc)})
    return {'processed': len(responses), 'results': responses}


def _extract_payload(record):
    body = record.get('body')
    if not body:
        return None
    message = json.loads(body)
    if 'Message' in message:
        return json.loads(message['Message'])
    return message


def _moderate_and_persist(message):
    content_id = message.get('contentId')
    bucket = message.get('mediaBucket')
    key = message.get('mediaKey')
    if not bucket or not key:
        raise ValueError('Missing S3 object info in message')

    analysis = analyze_media(bucket, key)
    persist_result(content_id, analysis, message)
    if analysis['blocked']:
        notify_admins(content_id, analysis, message)
    return {
        'status': 'OK',
        'contentId': content_id,
        'blocked': analysis['blocked'],
        'score': analysis['score'],
        'labels': analysis['labels'],
    }


def analyze_media(bucket, key):
    key_lower = key.lower()
    if key_lower.endswith(VIDEO_EXTENSIONS):
        labels = detect_video_labels(bucket, key)
    else:
        labels = detect_image_labels(bucket, key)
    max_confidence = max((label['Confidence'] for label in labels), default=0)
    blocked = max_confidence >= MEDIA_CONFIDENCE
    return {
        'score': max_confidence,
        'blocked': blocked,
        'labels': labels,
    }


def detect_image_labels(bucket, key):
    response = rekognition.detect_moderation_labels(
        Image={'S3Object': {'Bucket': bucket, 'Name': key}},
        MinConfidence=MEDIA_CONFIDENCE
    )
    return response.get('ModerationLabels', [])


def detect_video_labels(bucket, key):
    start = rekognition.start_content_moderation(
        Video={'S3Object': {'Bucket': bucket, 'Name': key}},
        MinConfidence=MEDIA_CONFIDENCE
    )
    job_id = start['JobId']
    collected = []
    token = None
    timeout = time.time() + 240
    while True:
        if time.time() > timeout:
            raise TimeoutError('Rekognition video moderation timed out')
        params = {'JobId': job_id, 'MaxResults': 200}
        if token:
            params['NextToken'] = token
        response = rekognition.get_content_moderation(**params)
        status = response.get('JobStatus')
        if status == 'IN_PROGRESS':
            time.sleep(5)
            continue
        if status not in ('SUCCEEDED', 'PARTIAL_SUCCESS'):
            raise RuntimeError(f'Video moderation failed: {status}')
        collected.extend(response.get('ModerationLabels', []))
        token = response.get('NextToken')
        if not token:
            break
    labels = []
    for record in collected:
        label = record.get('ModerationLabel', {})
        labels.append({
            'Name': label.get('Name'),
            'Confidence': float(label.get('Confidence', 0)),
            'Timestamp': record.get('Timestamp')
        })
    return labels


def persist_result(content_id, analysis, message):
    conn = _get_connection()
    with conn.cursor() as cursor:
        cursor.execute("SELECT id FROM moderated_content WHERE reference_id=%s", (content_id,))
        row = cursor.fetchone()
        if not row:
            raise ValueError(f'moderated_content row not found for {content_id}')
        db_id = row[0]
        status = 'BLOCKED' if analysis['blocked'] else 'APPROVED'
        reason = 'MEDIA_FLAGGED' if analysis['blocked'] else None
        cursor.execute(
            """
            UPDATE moderated_content
            SET media_score=%s,
                status=%s,
                blocked=%s,
                block_reason=%s,
                updated_at=UTC_TIMESTAMP()
            WHERE id=%s
            """,
            (analysis['score'], status, analysis['blocked'], reason, db_id)
        )
        if analysis['blocked']:
            cursor.execute(
                """
                INSERT INTO moderation_admin_alerts(content_id, type, reason, payload, created_at, acknowledged)
                VALUES(%s, %s, %s, %s, UTC_TIMESTAMP(), 0)
                """,
                (db_id, 'MEDIA', 'Lambda media moderation blocked the upload', json.dumps({
                    'contentId': content_id,
                    'labels': analysis['labels'],
                    'authorId': message.get('authorId')
                }))
            )
    conn.commit()


def notify_admins(content_id, analysis, message):
    if not ADMIN_ALERT_TOPIC:
        return
    sns.publish(
        TopicArn=ADMIN_ALERT_TOPIC,
        Subject='ModerationBlocked',
        Message=json.dumps({
            'contentId': content_id,
            'score': analysis['score'],
            'labels': analysis['labels'],
            'mediaBucket': message.get('mediaBucket'),
            'mediaKey': message.get('mediaKey'),
            'mediaUrl': message.get('mediaUrl'),
        }, default=_decimal_default)
    )


def _decimal_default(obj):
    if isinstance(obj, Decimal):
        return float(obj)
    return obj


def _get_connection():
    global connection
    if connection and connection.open:
        return connection
    connection = pymysql.connect(
        host=DB_HOST,
        user=DB_USER,
        password=DB_PASSWORD,
        db=DB_NAME,
        port=DB_PORT,
        autocommit=False,
        charset='utf8mb4'
    )
    return connection
