package ru.ytkab0bp.slicebeam.cloud;

import android.content.Intent;
import android.net.Uri;
import android.util.Log;

import com.google.gson.Gson;

import ru.ytkab0bp.sapil.APICallback;
import ru.ytkab0bp.sapil.APIRequestHandle;
import ru.ytkab0bp.slicebeam.R;
import ru.ytkab0bp.slicebeam.SliceBeam;
import ru.ytkab0bp.slicebeam.events.CloudFeaturesUpdatedEvent;
import ru.ytkab0bp.slicebeam.events.CloudLoginStateUpdatedEvent;
import ru.ytkab0bp.slicebeam.events.CloudModelsRemainingCountUpdatedEvent;
import ru.ytkab0bp.slicebeam.events.CloudUserInfoUpdatedEvent;
import ru.ytkab0bp.slicebeam.events.NeedDismissSnackbarEvent;
import ru.ytkab0bp.slicebeam.events.NeedSnackbarEvent;
import ru.ytkab0bp.slicebeam.utils.Prefs;
import ru.ytkab0bp.slicebeam.utils.ViewUtils;
import ru.ytkab0bp.slicebeam.view.SnackbarsLayout;

public class CloudController {
    public final static String USER_INFO_AI_GEN_TAG = "ai_gen_user_info";
    private final static String CLOUD_SYNC_TAG = "cloud_sync";

    private final static String TAG = "cloud";
    private final static long MIN_SYNC_DELTA = 5 * 60 * 1000L; // Once in 5 minutes
    private final static long MIN_SYNC_FEATURES_DELTA = 12 * 60 * 60 * 1000L; // Once in 12 hours

    private static boolean isSyncInProgress;
    private static CloudAPI.UserInfo userInfo;
    private static CloudAPI.UserFeatures userFeatures;

    private static int modelsUsed;
    private static int modelsMaxGenerations;
    private static boolean isLoggingIn;
    private static APIRequestHandle beginLoginHandle;
    private static String loginSessionId;
    private static Runnable loginAutoCancel = () -> {
        loginSessionId = null;
        isLoggingIn = false;
        SliceBeam.EVENT_BUS.fireEvent(new CloudLoginStateUpdatedEvent());
    };
    private static Runnable loginCheck = new Runnable() {
        @Override
        public void run() {
            CloudAPI.INSTANCE.loginCheck(loginSessionId, new APICallback<CloudAPI.LoginState>() {
                @Override
                public void onResponse(CloudAPI.LoginState response) {
                    if (response.loggedIn) {
                        Prefs.setCloudAPIToken(response.bearer);
                        loadUserInfo();
                        ViewUtils.removeCallbacks(loginAutoCancel);
                    } else if (isLoggingIn) {
                        ViewUtils.postOnMainThread(loginCheck, 5000);
                    }
                }

                @Override
                public void onException(Exception e) {
                    Log.e(TAG, "Failed to check login state", e);

                    if (isLoggingIn) {
                        ViewUtils.postOnMainThread(loginCheck, 5000);
                    }
                }
            });
        }
    };

    private static Gson gson = new Gson();

    public static void init() {
        if (Prefs.getCloudCachedUserFeatures() != null) {
            userFeatures = gson.fromJson(Prefs.getCloudCachedUserFeatures(), CloudAPI.UserFeatures.class);
            SliceBeam.EVENT_BUS.fireEvent(new CloudFeaturesUpdatedEvent());
        }
        long now = SliceBeam.TRUE_TIME.now().getTime();
        boolean needSyncInfo = userFeatures == null || now - Prefs.getCloudLastFeaturesSync() > MIN_SYNC_FEATURES_DELTA;
        if (needSyncInfo) {
            checkUserFeatures();
        }

        if (Prefs.getCloudAPIToken() != null) {
            if (Prefs.getCloudCachedUserInfo() != null) {
                userInfo = gson.fromJson(Prefs.getCloudCachedUserInfo(), CloudAPI.UserInfo.class);
                modelsUsed = Prefs.getCloudCachedUsedModels();
                modelsMaxGenerations = Prefs.getCloudCachedMaxModels();
            }

            if (needSyncInfo || userInfo == null) {
                loadUserInfo();
            }
        }
    }

    private static void loadUserInfo() {
        CloudAPI.INSTANCE.userGetInfo(new APICallback<CloudAPI.UserInfo>() {
            @Override
            public void onResponse(CloudAPI.UserInfo response) {
                userInfo = response;

                if (userInfo.id.equals("null")) {
                    userInfo = null;
                    Prefs.setCloudAPIToken(null);
                    Prefs.setCloudCachedUserInfo(null);
                    SliceBeam.EVENT_BUS.fireEvent(new CloudUserInfoUpdatedEvent());

                    if (isLoggingIn) {
                        isLoggingIn = false;
                        SliceBeam.EVENT_BUS.fireEvent(new CloudLoginStateUpdatedEvent());
                    }
                } else {
                    Prefs.setCloudCachedUserInfo(gson.toJson(userInfo));

                    SliceBeam.EVENT_BUS.fireEvent(new NeedDismissSnackbarEvent(USER_INFO_AI_GEN_TAG));
                    SliceBeam.EVENT_BUS.fireEvent(new CloudUserInfoUpdatedEvent());

                    if (isLoggingIn) {
                        isLoggingIn = false;
                        SliceBeam.EVENT_BUS.fireEvent(new CloudLoginStateUpdatedEvent());
                    }

                    if (isSyncAvailable() && Prefs.isCloudProfileSyncEnabled()) {
                        long now = SliceBeam.TRUE_TIME.now().getTime();
                        if (now != Prefs.getLocalLastModified()) {
                            sendData();
                        }
                    }
                    checkGeneratorRemaining();
                }
                Prefs.setCloudLastFeaturesSync(SliceBeam.TRUE_TIME.now().getTime());
            }

            @Override
            public void onException(Exception e) {
                Log.e(TAG, "Failed to get user info", e);
                ViewUtils.postOnMainThread(CloudController::init, 15000);
            }
        });
    }

    public static boolean isLoggingIn() {
        return isLoggingIn;
    }

    private static void beginLogin0() {
        beginLoginHandle = CloudAPI.INSTANCE.loginBegin(new APICallback<CloudAPI.LoginData>() {
            @Override
            public void onResponse(CloudAPI.LoginData response) {
                loginSessionId = response.sessionId;

                ViewUtils.postOnMainThread(loginAutoCancel, response.expiresAt * 1000L - SliceBeam.TRUE_TIME.now().getTime());
                ViewUtils.postOnMainThread(loginCheck, 5000);
                ViewUtils.postOnMainThread(() -> SliceBeam.INSTANCE.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(response.url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)));
            }

            @Override
            public void onException(Exception e) {
                ViewUtils.postOnMainThread(CloudController::beginLogin0, 15000);
            }
        });
    }

    public static void beginLogin() {
        isLoggingIn = true;
        SliceBeam.EVENT_BUS.fireEvent(new CloudLoginStateUpdatedEvent());
        beginLogin0();
    }

    public static void cancelLogin() {
        isLoggingIn = false;
        SliceBeam.EVENT_BUS.fireEvent(new CloudLoginStateUpdatedEvent());
        if (loginSessionId != null) {
            CloudAPI.INSTANCE.loginCancel(loginSessionId, response -> {});
        }
        if (beginLoginHandle != null && beginLoginHandle.isRunning()) {
            beginLoginHandle.cancel();
            beginLoginHandle = null;
        }
        ViewUtils.removeCallbacks(loginCheck);
        ViewUtils.removeCallbacks(loginAutoCancel);
        loginSessionId = null;
    }

    public static void logout() {
        Prefs.setCloudAPIToken(null);
        userInfo = null;
        SliceBeam.EVENT_BUS.fireEvent(new CloudLoginStateUpdatedEvent());
        SliceBeam.EVENT_BUS.fireEvent(new CloudUserInfoUpdatedEvent());
        CloudAPI.INSTANCE.logout(response -> {});
    }

    public static void checkGeneratorRemaining() {
        CloudAPI.INSTANCE.modelsGetRemainingCount(new APICallback<CloudAPI.ModelsRemainingCount>() {
            @Override
            public void onResponse(CloudAPI.ModelsRemainingCount response) {
                modelsUsed = response.used;
                modelsMaxGenerations = response.max;
                Prefs.setCloudCachedUsedMaxModels(modelsUsed, modelsMaxGenerations);
                SliceBeam.EVENT_BUS.fireEvent(new CloudModelsRemainingCountUpdatedEvent());
            }

            @Override
            public void onException(Exception e) {
                Log.e(TAG, "Failed to check remaining models", e);
                ViewUtils.postOnMainThread(CloudController::checkGeneratorRemaining, 15000);
            }
        });
    }

    public static void checkUserFeatures() {
        CloudAPI.INSTANCE.userGetFeatures(new APICallback<CloudAPI.UserFeatures>() {
            @Override
            public void onResponse(CloudAPI.UserFeatures response) {
                userFeatures = response;
                Prefs.setCloudCachedUserFeatures(gson.toJson(userFeatures));
                if (Prefs.getCloudAPIToken() == null) {
                    Prefs.setCloudLastFeaturesSync(SliceBeam.TRUE_TIME.now().getTime());
                }
                SliceBeam.EVENT_BUS.fireEvent(new CloudFeaturesUpdatedEvent());
            }

            @Override
            public void onException(Exception e) {
                Log.e(TAG, "Failed to get user features", e);
                ViewUtils.postOnMainThread(CloudController::checkUserFeatures, 15000);
            }
        });
    }

    public static CloudAPI.UserInfo getUserInfo() {
        return userInfo;
    }

    public static CloudAPI.UserFeatures getUserFeatures() {
        return userFeatures;
    }

    public static boolean isSyncAvailable() {
        return Prefs.getCloudAPIToken() != null && userInfo != null && userFeatures != null && userInfo.currentLevel >= userFeatures.syncRequiredLevel;
    }

    public static boolean needShowAIGenerator() {
        return userFeatures != null && userFeatures.aiGeneratorRequiredLevel >= 0;
    }

    public static int getGeneratedModels() {
        return modelsUsed;
    }

    public static int getMaxGeneratedModels() {
        return modelsMaxGenerations;
    }

    private static void sendData() {
        if (isSyncInProgress) {
            return;
        }
        // TODO: IMPORTANT: Check getState first, then show conflict info
        long modified = Prefs.getLocalLastModified();
        isSyncInProgress = true;
        SliceBeam.EVENT_BUS.fireEvent(new NeedSnackbarEvent(SnackbarsLayout.Type.LOADING, R.string.CloudSyncInProgress).tag(CLOUD_SYNC_TAG));
        CloudAPI.INSTANCE.syncUpload("", new APICallback<CloudAPI.SyncState>() {
            @Override
            public void onResponse(CloudAPI.SyncState response) {
                isSyncInProgress = false;
                if (Prefs.getLocalLastModified() != modified) { // Re-send otherwise
                    sendData();
                    return;
                }
                Prefs.setCloudLastSync(modified);
                SliceBeam.EVENT_BUS.fireEvent(new NeedDismissSnackbarEvent(CLOUD_SYNC_TAG));
                SliceBeam.EVENT_BUS.fireEvent(new NeedSnackbarEvent(R.string.CloudSyncSuccess));
            }

            @Override
            public void onException(Exception e) {
                Log.e(TAG, "Failed to upload sync data", e);
                isSyncInProgress = false;

                SliceBeam.EVENT_BUS.fireEvent(new NeedDismissSnackbarEvent(CLOUD_SYNC_TAG));
                SliceBeam.EVENT_BUS.fireEvent(new NeedSnackbarEvent(SnackbarsLayout.Type.ERROR, R.string.CloudSyncError));
            }
        });
    }

    public static void notifyDataChanged() {
        long now = SliceBeam.TRUE_TIME.now().getTime();
        Prefs.setLocalLastModified(now);
        if (!isSyncAvailable() || !Prefs.isCloudProfileSyncEnabled()) {
            return;
        }
        if (now - Prefs.getCloudLastSync() > MIN_SYNC_DELTA) {
            sendData();
        }
    }
}
