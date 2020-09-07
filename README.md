## Summary

This is the Android SDK of Futurae. You can read more about Futurae™ at [futurae.com].

## Table of contents

* [Basic integration](#basic-integration)
   * [Get FuturaeKit SDK for Android via Maven](#get-futuraekit-sdk-for-android-via-maven)
   * [Get FuturaeKit SDK for Android Manually](#get-futuraekit-sdk-for-android-manually)
   * [Add permissions](#add-permissions)
   * [Basic setup](#basic-setup)
   * [Build your app](#build-your-app)
   * [R8 / Proguard](#r8-proguard)
* [Features](#features)
   * [Callbacks](#callbacks)
   * [URI Schemes](#uri-schemes)
   * [Push Notifications](#push-notifications)
      * [FCM Token Registration](#fcm-token-registration)
      * [FCM Listener Service](#fcm-listener-service)
			* [Local Intents](#local-intents)
   * [Enroll User](#enroll-user)
   * [Logout User](#logout-user)
   * [Account Status](#account-status)
   * [Authenticate User](#authenticate-user)
      * [QR Code Factor](#qr-code-factor)
      * [Push Notification Factor](#push-notification-factor)
         * [Approve Authentication](#approve-authentication)
         * [Reject Authentication](#reject-authentication)
      * [TOTP Factor](#totp-factor)
      * [Session Information](#session-information)


## <a id="basic-integration" />Basic integration
We will describe the steps to integrate the FuturaeKit SDK into your Android project. We are going to assume that you are using Android Studio for your development.

### <a id="get-futuraekit-sdk-for-android-via-maven" />Get FuturaeKit SDK for Android via Maven
The *preferred* way to get the FuturaeKit SDK fro Android is through maven and the Futurae private repository.

To do so add the following lines to your `build.gradle` file:

```
repositories {
  maven {
    url "https://artifactory.futurae.com/artifactory/futurae-mobile"
    credentials {
      username = "anonymous"
      password = ""
    }
  }
}

dependencies {
  implementation('com.futurae.sdk:futuraekit:1.0.0')
}
```

Of course, make sure to specify the correct version number.

### <a id="get-futuraekit-sdk-for-android-via-maven" />Get FuturaeKit SDK for Android Manually
Alternatively, *although discouraged*, you can download the latest SDK from the [releases](https://git.futurae.com/futurae-public/futurae-android-sdk/tags), or clone this repository directly.
This repository also contains a simple demo app to show how the SDK can be integrated.

To integrate the FuturaeKit SDK into your project, copy `futuraekit.aar` into the `src/main/libs` folder of your app.
Then, in your modules `build.gradle` (the one under "app"), add the following dependencies:
```
implementation 'com.squareup.retrofit2:retrofit:2.3.0'
implementation 'com.squareup.retrofit2:converter-moshi:2.3.0'
implementation 'com.squareup.moshi:moshi-adapters:1.4.0'
implementation 'com.squareup.okhttp3:okhttp:3.8.0'
implementation 'com.squareup.okhttp3:logging-interceptor:3.8.0'
implementation 'com.github.nisrulz:easydeviceinfo-base:2.4.1'
implementation files('src/main/libs/futuraekit.aar')
```

![][gradle-app]

And in the projects `build.gradle` adjust the repositories to include the `libs` folder:
```
allprojects {
    repositories {
        google()
        jcenter()
        flatDir {
            dirs 'src/main/libs'
        }
    }
}
```

![][gradle-project]


### <a id="add-permissions" />Add permissions
Please add the following permissions, which the FuturaeKit SDK needs, if they are not already present in your AndroidManifest.xml file:

```
<uses-permission android:name="android.permission.INTERNET"/>
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE"/>

<uses-permission android:name="android.permission.CAMERA" />
<uses-feature android:name="android.hardware.camera" />

<meta-data
        android:name="com.google.android.gms.vision.DEPENDENCIES"
        android:value="barcode"/>
```


### <a id="basic-setup" />Basic setup
We recommend using an android [Application][android_application] class to initialize the SDK. If you already have one in your app already, follow these steps:

Firstly, in your `Application` class find or create the `onCreate` method and add the following code to initialize the FuturaeKit SDK:

```java
import com.futurae.sdk.FuturaeClient;

public class AppMain extends Application {

    // overrides
    @Override
    public final void onCreate() {

        super.onCreate();

        FuturaeClient.launch(this);
    }
}
```


Secondly, in your `res/values` folder make sure to create a file `futurae.xml` with the following contents:

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>

    <string name="ftr_sdk_id">{FuturaeSdkId}</string>
    <string name="ftr_sdk_key">{FuturaeSdkKey}</string>
    <string name="ftr_base_url">https://api.futurae.com:443</string>

</resources>
```

**Note**: Initializing the FuturaeKit SDK like this is `very important`. Replace `{FuturaeSdkId}` and `{FuturaeSdkKey}` with your SDK ID and key.

### <a id="build-your-app" />Build your app

Build and run your app. If the build succeeds, you should carefully read the SDK logs in the console.


### <a id="r8-proguard" />R8 / ProGuard

If you are using R8 or ProGuard to obfuscate your app, you need to include the proguard rules of the [futurae.pro](https://git.futurae.com/futurae-public/futurae-android-sdk/-/blob/master/FuturaeDemo/app/proguard/futurae.pro) file. See the [build.gradle](https://git.futurae.com/futurae-public/futurae-android-sdk/-/blob/master/FuturaeDemo/app/build.gradle) file of the FuturaeDemo app, as an example on how to do this.


## <a id="features" />Features
### <a id="callbacks" />Callbacks
The SDK methods that perform API calls use callbacks as the feedback mechanism. These calls expect an object of the `FuturaeCallback` interface as an argument:
```java
public interface FuturaeCallback {
    void success();
    void failure(Throwable throwable);
}
```

### <a id="uri-schemes" />URI Schemes
The SDK is able to handle URI scheme calls, which can be used to either **enroll** or **authenticate** users.
Once your activity has been set up to handle the URI scheme call intents, get the intent data in the `onCreate()` method of your activity, which contains the URI that should be passed in the SDK, using the `handleUri()` method:

```java
FuturaeClient.sharedClient().handleUri(uriString, new FuturaeCallback() {
	@Override
	public void success() {

	}

	@Override
	public void failure(Throwable throwable) {

	}
});
```

**Note**: In case you attempt an authentication with a URI scheme call, and this authentication includes extra information to be displayed to the user, you must retrieve this information from the server and include it in the authentication response; see section [Session Information](#session-information) for details on how to do that.

### <a id="push-notifications" />Push Notifications
Your app must be set up to receive Firebase Cloud Messaging (FCM) push notifications from our server. You can choose to receive and handle these notifications yourself, or alternatively you can use the existing infrastructure provided in the SDK. You can find more information on how to setup FCM push notifications for your app in the [Firebase Cloud Messaging Developer Guide](https://firebase.google.com/docs/cloud-messaging/).

In order to be able to receive FCM notifications, you need to specify the Firebase Messaging service inside the application section of your Manifest:
```xml
<service android:name="com.futurae.sdk.messaging.FTRFcmMessagingService">
	<intent-filter>
		<action android:name="com.google.firebase.MESSAGING_EVENT" />
	</intent-filter>
</service>
```

For this purpose, you can either use the one included in the SDK (`com.futurae.sdk.messaging.FTRFcmMessagingService`), or write your own. This service overrides two important methods:
```java
    @Override
    public void onNewToken(String token);

    @Override
    public void onMessageReceived(RemoteMessage message);
```

The first one is invoked whenever a new FCM push token has been generated for the app; it is important to register this token with the Futurae backend in order to continue receiving push notifications. The `FTRFcmMessagingService` implements this functionality.

The second one is invoked whenever a new push notification (or cloud message) is received by the app. The `FTRFcmMessagingService` then processes this message and invokes the SDK accordingly.

#### <a id="fcm-token-registration" />FCM Token Registration
The `FTRFcmMessagingService` is responsible for registering the app's FCM token to the Futurae server. This is important for the server to be able to issue FCM notifications for your app. The provided service handles this, however if you need to, you can write your own or extend the existing one.

If you are implementing **your own FCM notification handling**, you should register the FCM token to the Futurae server every time it changes. The call that registers the FCM token to the Futurae server is `registerPushToken()`, and it is necessary every time the FCM token is generated or is changed by FCM.

For example, once the app receives a new FCM token (e.g. via an `onNewToken()` callback in your own `FirebaseMessagingService` subclass), the token needs to be obtained and registered to the Futurae server using the following code:
```java
FuturaeClient.sharedClient().registerPushToken(fcmToken, new FuturaeCallback() {
	@Override
	public void success() {

	}

	@Override
	public void failure(Throwable t) {

	}
});
```

#### <a id="fcm-listener-service" />FCM Listener Service
The `FTRFcmMessagingService` receives FCM push notifications and handles them, according to the actions dictated by the Futurae server. You can use or extend the service provided by the SDK, or write your own. There are two distinct push notification types issued by the Futurae server: **Aprove** or **Unenroll**.

In case you want to process and handle the FCM notifications without using `FTRFcmMessagingService`, you must use the following code in order to process and handle the notifications sent by the Futurae server, inside the implementation of your own `FirebaseMessagingService` subclass:
```java
@Override
public void onMessageReceived(RemoteMessage message) {

	// Create and handle a Futurae notification, containing a Bundle with any data from message.getData()
	FTRNotification notification = notificationFactory.createNotification(service, data);
	notification.handle();
}
```

#### <a id="local-intents" />Local Intents
Once a Futurae FCM notification has been handled, the SDK will notify the host app using **local broadcasts**. The app should register a broadcast receiver for these intents and react accordingly. There are three distinct Intents that the notification handlers might send in a local broadcast:
* `INTENT_GENERIC_NOTIFICATION_ERROR`: Indicates that an error was encountered during the processing or handling of a FCM notification.
* `INTENT_APPROVE_AUTH_MESSAGE`: Indicates that a Push Notification Authentication has been initiated.
* `INTENT_ACCOUNT_UNENROLL_MESSAGE`: Indicates that a user account has been logged out remotely.

The following example shows how to register for these intents:
```java
IntentFilter intentFilter = new IntentFilter();
intentFilter.addAction(Shared.INTENT_GENERIC_NOTIFICATION_ERROR);  // General notification error intent
intentFilter.addAction(Shared.INTENT_APPROVE_AUTH_MESSAGE);        // Approve Authentication notification intent
intentFilter.addAction(Shared.INTENT_ACCOUNT_UNENROLL_MESSAGE);    // Logout user notification intent

LocalBroadcastManager.getInstance(getContext()).registerReceiver(new BroadcastReceiver() {

	@Override
	public void onReceive(Context context, Intent intent) {

		switch(intent.getAction()) {
			case Shared.INTENT_ACCOUNT_UNENROLL_MESSAGE:
				// Handle unenroll notification (e.g. refresh lists)
				break;

			case Shared.INTENT_APPROVE_AUTH_MESSAGE:
				// Handle approve notification (e.g. show approve view)
				break;

			case Shared.INTENT_GENERIC_NOTIFICATION_ERROR:
				// Handle FCM notification error
				break;
		}
	}
}, intentFilter);
```

### <a id="enroll-user" />Enroll User

To enroll a user, you must call the `enroll()` method, using a valid code obtained by scanning an enrolment QR Code. For example, you can use a QR Code Reader Activity to scan a code and obtain the result:
```java
startActivityForResult(FTRQRCodeActivity.getIntent(this), FTRQRCodeActivity.RESULT_BARCODE);
```

If a QR Code is successfully scanned then `onActivityResult` will be called, with the scanned code:
```java
@Override
protected void onActivityResult(int requestCode, int resultCode, Intent data) {

	if (resultCode == RESULT_OK && requestCode == FTRQRCodeActivity.RESULT_BARCODE) {
		Barcode qrcode = data.getParcelableExtra(FTRQRCodeActivity.PARAM_BARCODE);

		FuturaeClient.sharedClient().enroll(qrcode.rawValue, new FuturaeCallback() {
			@Override
			public void success() {

			}

			@Override
			public void failure(Throwable throwable) {

			}
		});
	}

}
```

Please make sure to call enroll, inside the onActivityResult method to complete the user enrollment.

### <a id="logout-user" />Logout User
To remove a user account from the app and the SDK, call the `logout()` method:
```java
FuturaeClient.sharedClient().logout(userId, new FuturaeCallback() {
	@Override
	public void success() {

	}

	@Override
	public void failure(Throwable throwable) {

	}
});
```

Typically this should happen either when the user removes the account manually from the app, or when a user has been logged out remotely by the server. In the former case, calling the `logout()` method is enough, as it notifies the server of the logout and deletes the account from the SDK too. In the latter case, the server will send a FCM notification to the app, and the default handler of the notification will delete the account from the SDK as well. In this case, the notification handler will also send a local broadcast to the app (see `INTENT_ACCOUNT_UNENROLL_MESSAGE` [above](#local-intents), so that the app can also perform any further action required (e.g. refresh the list of active accounts in an account view).

The intent contains the User ID as an extra:
```java
String userId = intent.getStringExtra(FTRUnenrollNotification.PARAM_USER_ID);
```

### <a id="account-status" />Account Status
To get a list of all enrolled accounts, call the following method:

```java
List<Account> accounts = FuturaeClient.sharedClient().getAccounts();
```

To fetch the status and current sessions for these accounts, you can use the following method (where `userIds` is a list of the corresponding user IDs):

```java
FuturaeClient.sharedClient().getAccountsStatus(userIds, new FuturaeResultCallback<AccountsStatus>() {
    @Override
    public void success(AccountsStatus accountsStatus) {
        // Handle the pending sessions
    }

    @Override
    public void failure(Throwable throwable) {
        // Handle the error
    }
});
```

**Hint:** You can use this method if you want to check if there are any pending sessions, e.g. when the app wakes up or the user refreshes the view.
For each account, a list of active sessions will be returned, and each session includes a `session ID` to proceed with the authentication.


### <a id="auth-user" />Authenticate User
To authenticate (or reject) a user session, depending on the authentication factor, you can use the following methods: **QR Code Factor**, or **Push Notification Factor**.

#### <a id="qrcode-auth" />QR Code Factor
To authenticate with the QR Code Factor, scan the QR Code provided by the server and pass its contents to the following method:
```java
FuturaeClient.sharedClient().approveAuth(qrCodeString, new FuturaeCallback() {
	@Override
	public void success() {

	}

	@Override
	public void failure(Throwable throwable) {

	}
});
```

**Note**: In case you attempt an authentication with a QR Code, and this authentication includes extra information to be displayed to the user, you must retrieve this information from the server and include it in the authentication response; see section [Session Information](#session-information) for details on how to do that.

#### <a id="approve-auth" />Push Notification Factor
When a Push Notification Factor session is initiated on the server side, the server will send a push notification to the app, where the user should approve or reject the authentication session. The notification is received and handled by the SDK, which in turn will send a local broadcast (see `INTENT_APPROVE_AUTH_MESSAGE` [above](#local-intents)), so that the app can perform any required actions. For example, the app might want to display a prompt to the user so that they can approve or reject the session.

The intent contains an object that describes the authentication session as an extra. This object contains the User ID and the Session ID, which are required for sending the authentication outcome to the server:
```java
ApproveSession authSession = intent.getParcelableExtra(FTRApproveNotification.PARAM_APPROVE_SESSION);
String userId = authSession.getUserId();
String sessionId = authSession.getSessionId();
```

Once the outcome of the authentication has been decided by the app, it should be sent to the server for the authentication session to complete.

**Note**: In case you attempt an authentication via push notification, and this authentication includes extra information to be displayed to the user, you must retrieve this information from the server and include it in the authentication response; see section [Session Information](#session-information) for details on how to do that.

##### <a id="approve-reply" />Approve Authentication
To approve the authentication session, use the following method:
```java
FuturaeClient.sharedClient().approveAuth(userId, sessionId, new FuturaeCallback() {
	@Override
	public void success() {

	}

	@Override
	public void failure(Throwable t) {

	}
});
```

##### <a id="reject-reply" />Reject Authentication
The user might choose to reject the authentication session. Additionally, in case the session has not been initiated by the user, they might also choose to report a fraudulent authentication attempt back to the server. In this case, use the following method:
```java
boolean reportFraud = false; // Set to true to report a fraudulent attempt
FuturaeClient.sharedClient().rejectAuth(userId, sessionId, reportFraud, new FuturaeCallback() {
	@Override
	public void success() {

	}

	@Override
	public void failure(Throwable t) {

	}
});
```

#### <a id="totp-auth" />TOTP Factor
The TOTP Factor can be used for offline authentication, as there is no requirement for an internet connection in the app. To get the current TOTP generated by the SDK for a specific user account, call the following method:
```java
CurrentTotp totp = FuturaeClient.sharedClient().nextTotp(userId);
String passcode = totp.getPasscode();            // The TOTP that the user should use to authenticate
int remainingSeconds = totp.getRemainingSecs();  // The remaining seconds of validity of this TOTP
```
As seen in this example, the `nextTotp()` method returns an object that contains the TOTP itself, but also the remaining seconds that this TOTP will be still valid for. After this time, a new TOTP must be obtained by the SDK.


#### <a id="session-information" />Session Information
For a given session, either identified via a `session ID` (e.g. received by a push notification) or a `session Token` (e.g. received by a QRCode scan), you can ask the server for more information about the session:

```java
// if you have a session ID
FuturaeClient.sharedClient().sessionInfoById(userId, sessionId, new FuturaeResultCallback<SessionInfo>() {
    @Override
    public void success(SessionInfo sessionInfo) {
        // Handle the session
    }

    @Override
    public void failure(Throwable throwable) {
        // Handle the error
    }
});

// if you have a session Token
FuturaeClient.sharedClient().sessionInfoByToken(userId, sessionId, new FuturaeResultCallback<SessionInfo>() {
    @Override
    public void success(SessionInfo sessionInfo) {
        // Handle the session
    }

    @Override
    public void failure(Throwable throwable) {
        // Handle the error
    }
});
```

If there is extra information to be displayed to the user, for example when confirming a transaction, this will be indicated with a list of key-value pairs in the `extra_info` part of the response.

In order to query the server for the session information, you need the user ID and the session ID or token. You can use the following helper methods to obtain these from either a URI or a QR Code:
```java
public class FuturaeClient {
    public static String getUserIdFromQrcode(String qrCode);
    public static String getSessionTokenFromQrcode(String qrCode);
    public static String getUserIdFromUri(String uri);
    public static String getSessionTokenFromUri(String uri);
}
```

##### <a id="auth-extra-info" />Authentication with extra information
In case it exists, you can retrieve the extra information that the user needs to review while authenticating by calling the following method on the `SessionInfo` object:

```java
public ApproveInfo[] getApproveInfo();
```

Each `ApproveInfo` object is a key-value pair that describes a single piece of information; call the following methods to retrieve the key and value of the object respectively.

```java
public String getKey();
public String getValue();
```

**IMPORTANT**: In case the authentication contains such extra information, it is mandatory that the information is fetched from the server, displayed to the user, and included in the authentication response in order for it to be included in the response signature; otherwise the server will reject the authentication response. Use the following methods to send an authentication response including the authentication information:

```java
class FuturaeClient {
    public void approveAuth(String qrCode, final FuturaeCallback callback, ApproveInfo[] extraInfo);
    public void approveAuth(String userId, String sessionId, final FuturaeCallback callback, ApproveInfo[] extraInfo);
    public void rejectAuth(String userId, String sessionId, boolean reportFraud, final FuturaeCallback callback, ApproveInfo[] extraInfo);
}
```

[futurae.com]:  http://www.futurae.com

[gradle-app]:        ./Resources/gradle-app.png
[gradle-project]:  ./Resources/gradle-project.png
[config-xml]:  ./Resources/config-xml.png

[android_application]:            http://developer.android.com/reference/android/app/Application.html
[build_gradle]:            ./app/build.gradle
