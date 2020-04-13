package com.zt.shareextend;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.PluginRegistry;
import io.flutter.plugin.common.PluginRegistry.Registrar;

/**
 * Plugin method host for presenting a share sheet via Intent
 */
public class ShareExtendPlugin implements MethodChannel.MethodCallHandler, PluginRegistry.RequestPermissionsResultListener {

    /// the authorities for FileProvider
    private static final int CODE_ASK_PERMISSION = 100;
    private static final String CHANNEL = "com.zt.shareextend/share_extend";
    private static final String INSTAGRAM_PACKAGE_NAME = "com.instagram.android";

    private final Registrar mRegistrar;
    private List<String> list;
    private String type;
    private String sharePanelTitle;
    private String subject;

    public static void registerWith(Registrar registrar) {
        MethodChannel channel = new MethodChannel(registrar.messenger(), CHANNEL);
        final ShareExtendPlugin instance = new ShareExtendPlugin(registrar);
        registrar.addRequestPermissionsResultListener(instance);
        channel.setMethodCallHandler(instance);
    }


    private ShareExtendPlugin(Registrar registrar) {
        this.mRegistrar = registrar;
    }

    @Override
    public void onMethodCall(MethodCall call, MethodChannel.Result result) {
        if (call.method.equals("share")) {
            if (!(call.arguments instanceof Map)) {
                throw new IllegalArgumentException("Map argument expected");
            }
            // Android does not support showing the share sheet at a particular point on screen.
            list = call.argument("list");
            type = call.argument("type");
            sharePanelTitle = call.argument("sharePanelTitle");
            subject = call.argument("subject");
            share(list, type, sharePanelTitle, subject);
            result.success(null);
        } else if (call.method.equals("shareToInstagram")) {
            type = call.argument("type");
            shareToInstagram(list, type);
            result.success(null);
        } else {
            result.notImplemented();
        }
    }

    private void share(List<String> list, String type, String sharePanelTitle, String subject) {
        if (list == null || list.isEmpty()) {
            throw new IllegalArgumentException("Non-empty list expected");
        }
        Intent shareIntent = new Intent();
        shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        shareIntent.putExtra(Intent.EXTRA_SUBJECT, subject);

        ArrayList<Uri> uriList = new ArrayList<>();
        if ("text".equals(type)) {
            shareIntent.setAction(Intent.ACTION_SEND);
            shareIntent.putExtra(Intent.EXTRA_TEXT, list.get(0));
            shareIntent.setType("text/plain");
        } else {
            if (ShareUtils.shouldRequestPermission(list)) {
                if (!checkPermission()) {
                    requestPermission();
                    return;
                }
            }

            for (String path : list) {
                File f = new File(path);
                Uri uri = ShareUtils.getUriForFile(mRegistrar.activity(), f);
                uriList.add(uri);
            }

            if ("image".equals(type)) {
                shareIntent.setType("image/*");
            } else if ("video".equals(type)) {
                shareIntent.setType("video/*");
            } else {
                shareIntent.setType("application/*");
            }
            if (uriList.size() == 1) {
                shareIntent.setAction(Intent.ACTION_SEND);
                shareIntent.putExtra(Intent.EXTRA_STREAM, uriList.get(0));
            } else {
                shareIntent.setAction(Intent.ACTION_SEND_MULTIPLE);
                shareIntent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uriList);
            }
        }
        startChooserActivity(uriList.get(0), shareIntent, sharePanelTitle);
    }

    private void startChooserActivity(Uri uri, Intent shareIntent,String sharePanelTitle) {
        Intent chooserIntent = Intent.createChooser(shareIntent, sharePanelTitle /* dialog subject optional */);
        if (mRegistrar.activity() != null) {
            List<ResolveInfo> resInfoList = mRegistrar.activity().getPackageManager().queryIntentActivities(chooserIntent, PackageManager.MATCH_DEFAULT_ONLY);
            for (ResolveInfo resolveInfo : resInfoList) {
                String packageName = resolveInfo.activityInfo.packageName;
                mRegistrar.activity().grantUriPermission(packageName, uri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
            }
            mRegistrar.activity().startActivity(chooserIntent);
        } else {
            chooserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            mRegistrar.context().startActivity(chooserIntent);
        }
    }

    private boolean checkPermission() {
        return ContextCompat.checkSelfPermission(mRegistrar.context(), Manifest.permission.WRITE_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void requestPermission() {
        ActivityCompat.requestPermissions(mRegistrar.activity(), new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, CODE_ASK_PERMISSION);
    }

    @Override
    public boolean onRequestPermissionsResult(int requestCode, String[] perms, int[] grantResults) {
        if (requestCode == CODE_ASK_PERMISSION && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            share(list, type, sharePanelTitle, subject);
        }
        return false;
    }

    private void openInstagramInPlayStore() {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.setData(Uri.parse("market://details?id="+ INSTAGRAM_PACKAGE_NAME));
        mRegistrar.context().startActivity(intent);
    }


    private boolean instagramInstalled() {
        boolean installed = false;

        try {
            mRegistrar.context()
                    .getPackageManager()
                    .getApplicationInfo(INSTAGRAM_PACKAGE_NAME, 0);

            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    private void shareToInstagram(List<String> list, String type) {
        String mediaType = "";
        if ("image".equals(type)) {
            mediaType = "image/jpeg";
        } else {
            mediaType = "video/*";
        }

        if (ShareUtils.shouldRequestPermission(list)) {
            if (!checkPermission()) {
                requestPermission();
                return;
            }
        }

        for (String path : list) {
            File f = new File(path);
            Uri uri = ShareUtils.getUriForFile(mRegistrar.activity(), f);
            uriList.add(uri);
        }


        if (instagramInstalled()) {
            StrictMode.VmPolicy.Builder builder = new StrictMode.VmPolicy.Builder();
            StrictMode.setVmPolicy(builder.build());

            Intent shareIntent = new Intent();
            shareIntent.setAction(Intent.ACTION_SEND);
            shareIntent.setPackage(INSTAGRAM_PACKAGE_NAME);
            shareIntent.setType(mediaType);
            shareIntent.putExtra(Intent.EXTRA_STREAM, (uriList[0]));
            shareIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            try {
                mRegistrar.context().startActivity(shareIntent);
            } catch (ActivityNotFoundException ex) {
                openInstagramInPlayStore();
            }
        } else {
            openInstagramInPlayStore();
        }
    }
}
