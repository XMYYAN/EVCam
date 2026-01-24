package com.kooo.evcam;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;

/**
 * 唤醒工具类
 * 用于在后台收到钉钉命令时保持CPU运行并启动 Activity
 * 注意：不会亮屏，支持息屏状态下静默拍照/录制
 */
public class WakeUpHelper {
    private static final String TAG = "WakeUpHelper";

    // CPU唤醒锁（不亮屏）
    private static PowerManager.WakeLock wakeLock;

    /**
     * 检查是否有悬浮窗权限（用于后台启动Activity）
     * Android 10+ 需要此权限才能从后台启动 Activity
     */
    public static boolean hasOverlayPermission(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return Settings.canDrawOverlays(context);
        }
        return true;
    }

    /**
     * 请求悬浮窗权限
     * 需要用户手动授权
     */
    public static void requestOverlayPermission(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        }
    }

    /**
     * 获取CPU唤醒锁（不亮屏）
     * 确保在息屏状态下CPU保持运行，能够完成拍照/录制
     */
    public static void acquireCpuWakeLock(Context context) {
        AppLog.d(TAG, "Acquiring CPU wake lock (screen stays off)...");

        PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        if (pm == null) {
            AppLog.e(TAG, "PowerManager is null");
            return;
        }

        // 释放之前的唤醒锁
        releaseWakeLock();

        // 创建新的唤醒锁
        // PARTIAL_WAKE_LOCK: 只保持CPU运行，不亮屏
        wakeLock = pm.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "EVCam:RemoteCommand"
        );

        // 持有唤醒锁 5 分钟（足够完成拍照或录制+上传）
        wakeLock.acquire(5 * 60 * 1000);
        AppLog.d(TAG, "CPU WakeLock acquired for 5 minutes");
    }

    /**
     * 释放唤醒锁
     */
    public static void releaseWakeLock() {
        if (wakeLock != null && wakeLock.isHeld()) {
            try {
                wakeLock.release();
                AppLog.d(TAG, "WakeLock released");
            } catch (Exception e) {
                AppLog.e(TAG, "Failed to release WakeLock", e);
            }
        }
        wakeLock = null;
    }

    /**
     * 启动 MainActivity 到前台，并传递命令参数
     * 
     * @param context 上下文
     * @param action 动作类型: "record" 或 "photo"
     * @param conversationId 钉钉会话ID
     * @param conversationType 钉钉会话类型
     * @param userId 钉钉用户ID
     * @param duration 录制时长（仅 record 时有效）
     */
    public static void launchMainActivityWithCommand(Context context, String action,
            String conversationId, String conversationType, String userId, int duration) {
        
        AppLog.d(TAG, "Launching MainActivity with command: " + action);

        // 获取CPU唤醒锁，确保息屏状态下也能执行
        acquireCpuWakeLock(context);

        // 创建 Intent
        Intent intent = new Intent(context, MainActivity.class);
        
        // 设置 flags
        // FLAG_ACTIVITY_NEW_TASK: 从非 Activity 上下文启动时必须
        // FLAG_ACTIVITY_CLEAR_TOP: 如果 Activity 已存在，清除其上的所有 Activity
        // FLAG_ACTIVITY_SINGLE_TOP: 如果 Activity 在栈顶，不创建新实例，调用 onNewIntent
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK |
                Intent.FLAG_ACTIVITY_CLEAR_TOP |
                Intent.FLAG_ACTIVITY_SINGLE_TOP);

        // 传递命令参数
        intent.putExtra("remote_action", action);
        intent.putExtra("remote_conversation_id", conversationId);
        intent.putExtra("remote_conversation_type", conversationType);
        intent.putExtra("remote_user_id", userId);
        intent.putExtra("remote_duration", duration);

        // 启动 Activity
        context.startActivity(intent);
        AppLog.d(TAG, "MainActivity launch intent sent");
    }

    /**
     * 启动 MainActivity 执行录制命令
     */
    public static void launchForRecording(Context context, String conversationId,
            String conversationType, String userId, int durationSeconds) {
        launchMainActivityWithCommand(context, "record", conversationId, conversationType, userId, durationSeconds);
    }

    /**
     * 启动 MainActivity 执行拍照命令
     */
    public static void launchForPhoto(Context context, String conversationId,
            String conversationType, String userId) {
        launchMainActivityWithCommand(context, "photo", conversationId, conversationType, userId, 0);
    }
}
