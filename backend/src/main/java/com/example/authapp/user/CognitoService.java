package com.example.authapp.user;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.cognitoidp.AWSCognitoIdentityProvider;
import com.amazonaws.services.cognitoidp.AWSCognitoIdentityProviderClientBuilder;
import com.amazonaws.services.cognitoidp.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.annotation.PostConstruct;

@Service
public class CognitoService {

    private static final Logger log = LoggerFactory.getLogger(CognitoService.class);

    @Value("${cloud.aws.region.static}")
    private String awsRegion;

    @Value("${com.example.authapp.cognito.access-key}")
    private String awsAccessKey;

    @Value("${com.example.authapp.cognito.secret-key}")
    private String awsSecretKey;

    @Value("${com.example.authapp.cognito.userPoolId}")
    private String userPoolId;

    @Value("${com.example.authapp.cognito.clientId}")
    private String clientId;

    @Value("${com.example.authapp.cognito.clientSecret:}")
    private String clientSecret;

    private AWSCognitoIdentityProvider cognitoClient;

    @PostConstruct
    public void init() {
        AWSCredentialsProvider awsCredentialsProvider;
        if (StringUtils.hasText(awsAccessKey) && StringUtils.hasText(awsSecretKey)) {
            awsCredentialsProvider = new AWSStaticCredentialsProvider(new BasicAWSCredentials(awsAccessKey, awsSecretKey));
            log.info("CognitoService initialized with static AWS credentials.");
        } else {
            awsCredentialsProvider = DefaultAWSCredentialsProviderChain.getInstance();
            log.info("CognitoService initialized with default AWS credentials provider chain.");
        }

        cognitoClient = AWSCognitoIdentityProviderClientBuilder.standard()
                .withCredentials(awsCredentialsProvider)
                .withRegion(Regions.fromName(awsRegion))
                .build();
    }

    public void signUp(String username, String password, String email, String nickname) {
        try {
            AttributeType emailAttr = new AttributeType().withName("email").withValue(email);
            AttributeType nicknameAttr = new AttributeType().withName("nickname").withValue(nickname);
            AttributeType preferredUsernameAttr = new AttributeType()
                    .withName("preferred_username")
                    .withValue(username);

            SignUpRequest signUpRequest = new SignUpRequest()
                    .withClientId(clientId)
                    .withUsername(username)
                    .withPassword(password)
                    .withUserAttributes(emailAttr, nicknameAttr, preferredUsernameAttr);

            cognitoClient.signUp(signUpRequest);
            System.out.println("User " + username + " signed up successfully.");
        } catch (UsernameExistsException e) {
            throw new DuplicateUsernameException("Username already registered with Cognito: " + username);
        } catch (Exception e) {
            throw new RuntimeException("Error during Cognito sign up: " + e.getMessage(), e);
        }
    }

    public AdminInitiateAuthResult signIn(String username, String password) {
        try {
            AdminInitiateAuthRequest authRequest = new AdminInitiateAuthRequest()
                    .withAuthFlow(AuthFlowType.ADMIN_NO_SRP_AUTH)
                    .withUserPoolId(userPoolId)
                    .withClientId(clientId)
                    .withAuthParameters(
                            new java.util.HashMap<String, String>() {{
                                put("USERNAME", username);
                                put("PASSWORD", password);
                            }}
                    );

            AdminInitiateAuthResult authResult = cognitoClient.adminInitiateAuth(authRequest);
            System.out.println("User " + username + " signed in successfully.");
            return authResult;
        } catch (Exception e) {
            throw new RuntimeException("Error during Cognito sign in: " + e.getMessage(), e);
        }
    }

    public void signOut(String accessToken) {
        try {
            GlobalSignOutRequest signOutRequest = new GlobalSignOutRequest()
                    .withAccessToken(accessToken);
            cognitoClient.globalSignOut(signOutRequest);
            System.out.println("User signed out successfully.");
        } catch (Exception e) {
            throw new RuntimeException("Error during Cognito sign out: " + e.getMessage(), e);
        }
    }

    public void revokeRefreshToken(String refreshToken) {
        if (!StringUtils.hasText(refreshToken)) {
            return;
        }
        try {
            RevokeTokenRequest revokeTokenRequest = new RevokeTokenRequest()
                    .withClientId(clientId)
                    .withToken(refreshToken);
            if (StringUtils.hasText(clientSecret)) {
                revokeTokenRequest = revokeTokenRequest.withClientSecret(clientSecret);
            }
            cognitoClient.revokeToken(revokeTokenRequest);
            log.info("Refresh token revoked successfully.");
        } catch (Exception e) {
            throw new RuntimeException("Error revoking refresh token: " + e.getMessage(), e);
        }
    }

    public void confirmSignUp(String username, String confirmationCode) {
        try {
            ConfirmSignUpRequest confirmSignUpRequest = new ConfirmSignUpRequest()
                    .withClientId(clientId)
                    .withUsername(username)
                    .withConfirmationCode(confirmationCode);
            cognitoClient.confirmSignUp(confirmSignUpRequest);
            System.out.println("User " + username + " confirmed successfully.");
        } catch (Exception e) {
            throw new RuntimeException("Error during Cognito confirm sign up: " + e.getMessage(), e);
        }
    }

    public void resendConfirmationCode(String username) {
        try {
            ResendConfirmationCodeRequest resendConfirmationCodeRequest = new ResendConfirmationCodeRequest()
                    .withClientId(clientId)
                    .withUsername(username);
            cognitoClient.resendConfirmationCode(resendConfirmationCodeRequest);
            System.out.println("Confirmation code resent to " + username + " successfully.");
        } catch (Exception e) {
            throw new RuntimeException("Error during Cognito resend confirmation code: " + e.getMessage(), e);
        }
    }

    public void addUserToGroup(String username, String groupName) {
        try {
            AdminAddUserToGroupRequest request = new AdminAddUserToGroupRequest()
                    .withUserPoolId(userPoolId)
                    .withUsername(username)
                    .withGroupName(groupName);
            cognitoClient.adminAddUserToGroup(request);
        } catch (Exception e) {
            throw new RuntimeException("Error adding user " + username + " to group " + groupName + ": " + e.getMessage(), e);
        }
    }

    public String findUsernameByEmail(String email) {
        try {
            ListUsersRequest listUsersRequest = new ListUsersRequest()
                    .withUserPoolId(userPoolId)
                    .withFilter("email = \"" + email + "\"");

            ListUsersResult listUsersResult = cognitoClient.listUsers(listUsersRequest);

            if (listUsersResult.getUsers() != null && !listUsersResult.getUsers().isEmpty()) {
                // Assuming email is unique and only one user will be found
                return listUsersResult.getUsers().get(0).getUsername();
            } else {
                return null; // No user found with that email
            }
        } catch (Exception e) {
            throw new RuntimeException("Error finding username by email in Cognito: " + e.getMessage(), e);
        }
    }

    public void forgotPassword(String username) {
        try {
            ForgotPasswordRequest forgotPasswordRequest = new ForgotPasswordRequest()
                    .withClientId(clientId)
                    .withUsername(username);
            cognitoClient.forgotPassword(forgotPasswordRequest);
            System.out.println("Forgot password request initiated for user: " + username);
        } catch (Exception e) {
            throw new RuntimeException("Error initiating forgot password for user " + username + ": " + e.getMessage(), e);
        }
    }

    public void confirmForgotPassword(String username, String confirmationCode, String newPassword) {
        try {
            ConfirmForgotPasswordRequest confirmForgotPasswordRequest = new ConfirmForgotPasswordRequest()
                    .withClientId(clientId)
                    .withUsername(username)
                    .withConfirmationCode(confirmationCode)
                    .withPassword(newPassword);
            cognitoClient.confirmForgotPassword(confirmForgotPasswordRequest);
            System.out.println("Password for user " + username + " reset successfully.");
        } catch (Exception e) {
            throw new RuntimeException("Error confirming forgot password for user " + username + ": " + e.getMessage(), e);
        }
    }

    public void updateUserAttributes(String username, java.util.List<AttributeType> attributes) {
        try {
            AdminUpdateUserAttributesRequest request = new AdminUpdateUserAttributesRequest()
                    .withUserPoolId(userPoolId)
                    .withUsername(username)
                    .withUserAttributes(attributes);
            cognitoClient.adminUpdateUserAttributes(request);
            System.out.println("User attributes for " + username + " updated successfully.");
        } catch (Exception e) {
            throw new RuntimeException("Error updating user attributes for " + username + ": " + e.getMessage(), e);
        }
    }

    public void adminDeleteUser(String username) {
        try {
            AdminDeleteUserRequest adminDeleteUserRequest = new AdminDeleteUserRequest()
                    .withUserPoolId(userPoolId)
                    .withUsername(username);
            cognitoClient.adminDeleteUser(adminDeleteUserRequest);
            System.out.println("User " + username + " deleted successfully from Cognito.");
        } catch (Exception e) {
            throw new RuntimeException("Error deleting user " + username + " from Cognito: " + e.getMessage(), e);
        }
    }

    public void adminDisableUser(String username) {
        try {
            AdminDisableUserRequest request = new AdminDisableUserRequest()
                    .withUserPoolId(userPoolId)
                    .withUsername(username);
            cognitoClient.adminDisableUser(request);
        } catch (Exception e) {
            throw new RuntimeException("Error disabling user " + username + " in Cognito: " + e.getMessage(), e);
        }
    }

    public void adminEnableUser(String username) {
        try {
            AdminEnableUserRequest request = new AdminEnableUserRequest()
                    .withUserPoolId(userPoolId)
                    .withUsername(username);
            cognitoClient.adminEnableUser(request);
        } catch (Exception e) {
            throw new RuntimeException("Error enabling user " + username + " in Cognito: " + e.getMessage(), e);
        }
    }

    public java.util.List<String> listGroups(String username) {
        try {
            AdminListGroupsForUserRequest request = new AdminListGroupsForUserRequest()
                    .withUserPoolId(userPoolId)
                    .withUsername(username);
            AdminListGroupsForUserResult result = cognitoClient.adminListGroupsForUser(request);
            java.util.List<String> groups = new java.util.ArrayList<>();
            if (result.getGroups() != null) {
                result.getGroups().forEach(g -> groups.add(g.getGroupName()));
            }
            return groups;
        } catch (Exception e) {
            throw new RuntimeException("Error listing Cognito groups for user " + username + ": " + e.getMessage(), e);
        }
    }
}
