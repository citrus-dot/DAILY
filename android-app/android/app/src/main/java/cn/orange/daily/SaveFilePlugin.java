package cn.orange.daily;

import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.ActivityCallback;
import androidx.activity.result.ActivityResult;
import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * SaveFile —— 调起 Android 存储访问框架（SAF）的「保存」文档选择器，
 * 让用户在 文件管理 / Download / Documents / SD 卡 中自由挑选保存位置，
 * 再用 ContentResolver 把文本写入所选 Uri。
 *
 * Web 侧调用：Capacitor.Plugins.SaveFile.save({ content, fileName, mimeType })
 *   - 成功：call.resolve()
 *   - 取消：call.reject("用户取消了保存", "CANCELLED")  → Web 回退分享面板
 *   - 失败：call.reject("保存失败：…", "WRITE_ERROR")    → Web 回退分享面板
 */
@CapacitorPlugin(name = "SaveFile")
public class SaveFilePlugin extends Plugin {

  @PluginMethod
  public void save(PluginCall call) {
    String content = call.getString("content");
    if (content == null) content = "";
    String fileName = call.getString("fileName");
    if (fileName == null || fileName.isEmpty()) fileName = "export.txt";
    String mimeType = call.getString("mimeType");
    if (mimeType == null || mimeType.isEmpty()) mimeType = "text/plain";
    // SAF 对带 ";charset=..." 的 type 支持不佳，提取纯 MIME（如 text/csv）
    int sc = mimeType.indexOf(';');
    if (sc >= 0) mimeType = mimeType.substring(0, sc).trim();
    if (mimeType.isEmpty()) mimeType = "text/plain";

    Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
    intent.addCategory(Intent.CATEGORY_OPENABLE);
    intent.setType(mimeType);
    intent.putExtra(Intent.EXTRA_TITLE, fileName);

    startActivityForResult(call, intent, "handleSaveResult");
  }

  @ActivityCallback
  private void handleSaveResult(PluginCall call, ActivityResult result) {
    if (result.getResultCode() != Activity.RESULT_OK || result.getData() == null) {
      call.reject("用户取消了保存", "CANCELLED");
      return;
    }
    Uri uri = result.getData().getData();
    if (uri == null) {
      call.reject("未获得保存位置", "NO_URI");
      return;
    }
    try {
      String content = call.getString("content");
      if (content == null) content = "";
      OutputStream os = getActivity().getContentResolver().openOutputStream(uri);
      if (os == null) {
        call.reject("无法写入该位置", "WRITE_NULL");
        return;
      }
      os.write(content.getBytes(StandardCharsets.UTF_8));
      os.close();
      call.resolve();
    } catch (Exception e) {
      String msg = (e != null && e.getMessage() != null) ? e.getMessage() : "未知错误";
      call.reject("保存失败：" + msg, "WRITE_ERROR");
    }
  }
}
