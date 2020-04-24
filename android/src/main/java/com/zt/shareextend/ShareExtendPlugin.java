package com.zt.shareextend;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import io.flutter.embedding.engine.plugins.FlutterPlugin;
import io.flutter.embedding.engine.plugins.activity.ActivityAware;
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding;
import io.flutter.plugin.common.BinaryMessenger;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.PluginRegistry;
import io.flutter.plugin.common.PluginRegistry.Registrar;

/**
 * Plugin method host for presenting a share sheet via Intent
 */
public class ShareExtendPlugin implements MethodChannel.MethodCallHandler, FlutterPlugin, PluginRegistry.RequestPermissionsResultListener, ActivityAware {

    /// the authorities for FileProvider
    private static final int CODE_ASK_PERMISSION = 100;
    private static final String CHANNEL = "com.zt.shareextend/share_extend";

    private Registrar mRegistrar;
    private Activity mActivity;
    private List<String> list;
    private String type;
    private String sharePanelTitle;
    private String subject;

    private @Nullable
    FlutterPluginBinding flutterPluginBinding;

    private ShareExtendPlugin(Registrar registrar) {
        this.mRegistrar = registrar;
    }

    private ShareExtendPlugin(Activity activity) {
        this.mActivity = activity;
    }

    public static void registerWith(Registrar registrar) {
        MethodChannel channel = new MethodChannel(registrar.messenger(), CHANNEL);
        final ShareExtendPlugin instance = new ShareExtendPlugin(registrar);
        registrar.addRequestPermissionsResultListener(instance);
        channel.setMethodCallHandler(instance);
    }

    @Override
    public void onAttachedToEngine(@NonNull FlutterPluginBinding binding) {
        this.flutterPluginBinding = binding;
    }

    @Override
    public void onDetachedFromEngine(@NonNull FlutterPluginBinding binding) {
        this.flutterPluginBinding = null;
    }

    @Override
    public void onAttachedToActivity(@NonNull ActivityPluginBinding binding) {
        Log.e("ACTIVITY:", "ATTACHING");
        binding.addRequestPermissionsResultListener(this);
        if (flutterPluginBinding != null) {
            maybeStartListening(
                    binding.getActivity(),
                    flutterPluginBinding.getBinaryMessenger());
        }
    }

    @Override
    public void onDetachedFromActivityForConfigChanges() {

    }

    @Override
    public void onReattachedToActivityForConfigChanges(ActivityPluginBinding binding) {
        onAttachedToActivity(binding);
    }

    @Override
    public void onDetachedFromActivity() {

    }

    private void maybeStartListening(
            Activity activity,
            BinaryMessenger messenger) {
        MethodChannel channel = new MethodChannel(messenger, CHANNEL);
        final ShareExtendPlugin instance = new ShareExtendPlugin(activity);
        channel.setMethodCallHandler(instance);
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
        } else {
            result.notImplemented();
        }
    }

    private void share(List<String> list, String type, String sharePanelTitle, String subject) {
        Log.e("ACTIVITY:", "Share");
        if (getContext() != null) {
            if (list == null || list.isEmpty()) {
                throw new IllegalArgumentException("Non-empty list expected");
            }
            Intent shareIntent = new Intent();
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            shareIntent.putExtra(Intent.EXTRA_SUBJECT, subject);

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

                ArrayList<Uri> uriList = new ArrayList<>();
                for (String path : list) {
                    File f = new File(path);
                    Uri uri = ShareUtils.getUriForFile(getContext(), f);
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
                ShareUtils.grantUriPermission(getContext(), uriList, shareIntent);
            }
            startChooserActivity(shareIntent, sharePanelTitle);
        }
    }

    private Context getContext() {
        Log.e("ACTIVITY:", String.valueOf(mActivity != null));
        return mActivity != null ? mActivity : (mRegistrar.activity() != null ? mRegistrar.activity() : mRegistrar.context());
    }

    private void startChooserActivity(Intent shareIntent, String sharePanelTitle) {
        Intent chooserIntent = Intent.createChooser(shareIntent, sharePanelTitle);
        if (getContext() != null) {
            getContext().startActivity(chooserIntent);
        } else {
            chooserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            getContext().startActivity(chooserIntent);
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
}
