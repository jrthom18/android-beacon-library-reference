package org.altbeacon.beaconreference;

import android.Manifest;
import android.annotation.TargetApi;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.Build;
import android.os.Bundle;
import android.os.RemoteException;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.support.v4.content.ContextCompat;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import com.facebook.AccessToken;
import com.facebook.AccessTokenTracker;
import com.facebook.CallbackManager;
import com.facebook.FacebookCallback;
import com.facebook.FacebookSdk;
import com.facebook.Profile;
import com.facebook.ProfileTracker;
import com.facebook.login.LoginResult;
import com.facebook.FacebookException;
import com.facebook.login.widget.LoginButton;

import org.altbeacon.beacon.BeaconConsumer;
import org.altbeacon.beacon.BeaconManager;
import org.altbeacon.beacon.BeaconParser;
import org.altbeacon.beacon.MonitorNotifier;
import org.altbeacon.beacon.Region;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 *
 * @author dyoung
 * @author Matt Tyler
 */
public class MonitoringActivity extends Activity  {
	protected static final String TAG = "MonitoringActivity";
	private static final int PERMISSION_REQUEST_FINE_LOCATION = 1;

    private CallbackManager callbackManager;
    private AccessTokenTracker accessTokenTracker;
    private ProfileTracker profileTracker;

    //Facebook login button
    private FacebookCallback<LoginResult> callback = new FacebookCallback<LoginResult>() {
        @Override
        public void onSuccess(LoginResult loginResult) {
            Profile profile = Profile.getCurrentProfile();
            nextActivity(profile);
        }

        private void nextActivity(Profile profile) {
        }

        @Override
        public void onCancel() {        }
        @Override
        public void onError(FacebookException e) {        }
    };

    @Override
	protected void onCreate(Bundle savedInstanceState) {
		Log.d(TAG, "onCreate");
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_monitoring);
		verifyBluetooth();

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
			// Android M Permission check
			if (this.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
				final AlertDialog.Builder builder = new AlertDialog.Builder(this);
				builder.setTitle("This app needs location access");
				builder.setMessage("Please grant location access so this app can detect beacons in the background.");
				builder.setPositiveButton(android.R.string.ok, null);
				builder.setOnDismissListener(new DialogInterface.OnDismissListener() {

					@TargetApi(23)
					@Override
					public void onDismiss(DialogInterface dialog) {
						requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
								PERMISSION_REQUEST_FINE_LOCATION);
					}

				});
				builder.show();
			}
		}
		else{
			if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
				final AlertDialog.Builder builder = new AlertDialog.Builder(this);
				builder.setTitle("This app needs location access");
				builder.setMessage("Please grant location access so this app can detect beacons in the background.");
				builder.setPositiveButton(android.R.string.ok, null);
				builder.setOnDismissListener(new DialogInterface.OnDismissListener() {

					@TargetApi(23)
					@Override
					public void onDismiss(DialogInterface dialog) {
						requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
								PERMISSION_REQUEST_FINE_LOCATION);
					}

				});
				builder.show();
			}
		}

        // Facebook Login Process
        FacebookSdk.sdkInitialize(getApplicationContext());
        callbackManager = CallbackManager.Factory.create();
        accessTokenTracker = new AccessTokenTracker() {
            @Override
            protected void onCurrentAccessTokenChanged(AccessToken oldToken, AccessToken newToken) {
            }
        };

        profileTracker = new ProfileTracker() {
            @Override
            protected void onCurrentProfileChanged(Profile oldProfile, Profile newProfile) {
                // TODO: SEND PROFILE DATA TO CLOUD, UPDATE USER ID FOR USE IN RangingActivity.java
                Profile profile = newProfile;

            }
        };
        accessTokenTracker.startTracking();
        profileTracker.startTracking();

        LoginButton loginButton = (LoginButton)findViewById(R.id.login_button);

        callback = new FacebookCallback<LoginResult>() {
            @Override
            public void onSuccess(LoginResult loginResult) {
                AccessToken accessToken = loginResult.getAccessToken();
                //Profile profile = Profile.getCurrentProfile();
                //nextActivity(profile);
                Toast.makeText(getApplicationContext(), "Logging in...", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onCancel() {
            }

            @Override
            public void onError(FacebookException e) {
            }
        };

        loginButton.setReadPermissions("user_friends");
        loginButton.registerCallback(callbackManager, callback);
	}

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        // if you don't add following block,
        // your registered `FacebookCallback` won't be called
        if (callbackManager.onActivityResult(requestCode, resultCode, data)) {
            return;
        }
    }

	@Override
	public void onRequestPermissionsResult(int requestCode,
										   String permissions[], int[] grantResults) {
		switch (requestCode) {
			case PERMISSION_REQUEST_FINE_LOCATION: {
				if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
					Log.d(TAG, "coarse location permission granted");
				} else {
					final AlertDialog.Builder builder = new AlertDialog.Builder(this);
					builder.setTitle("Functionality limited");
					builder.setMessage("Since location access has not been granted, this app will not be able to discover beacons when in the background.");
					builder.setPositiveButton(android.R.string.ok, null);
					builder.setOnDismissListener(new DialogInterface.OnDismissListener() {

						@Override
						public void onDismiss(DialogInterface dialog) {
						}

					});
					builder.show();
				}
				return;
			}
		}
	}

	public void onRangingClicked(View view) {
		Intent myIntent = new Intent(this, RangingActivity.class);
		this.startActivity(myIntent);
	}

	@Override
	public void onResume() {
		super.onResume();
		((BeaconReferenceApplication) this.getApplicationContext()).setMonitoringActivity(this);
	}

	@Override
	public void onPause() {
		super.onPause();
		((BeaconReferenceApplication) this.getApplicationContext()).setMonitoringActivity(null);
	}

	private void verifyBluetooth() {

		try {
			if (!BeaconManager.getInstanceForApplication(this).checkAvailability()) {
				final AlertDialog.Builder builder = new AlertDialog.Builder(this);
				builder.setTitle("Bluetooth not enabled");
				builder.setMessage("Please enable bluetooth in settings and restart this application.");
				builder.setPositiveButton(android.R.string.ok, null);
				builder.setOnDismissListener(new DialogInterface.OnDismissListener() {
					@Override
					public void onDismiss(DialogInterface dialog) {
						finish();
						System.exit(0);
					}
				});
				builder.show();
			}
		}
		catch (RuntimeException e) {
			final AlertDialog.Builder builder = new AlertDialog.Builder(this);
			builder.setTitle("Bluetooth LE not available");
			builder.setMessage("Sorry, this device does not support Bluetooth LE.");
			builder.setPositiveButton(android.R.string.ok, null);
			builder.setOnDismissListener(new DialogInterface.OnDismissListener() {

				@Override
				public void onDismiss(DialogInterface dialog) {
					finish();
					System.exit(0);
				}

			});
			builder.show();

		}

	}

}
