package cn.orange.daily;

import com.getcapacitor.BridgeActivity;
import android.os.Bundle;

public class MainActivity extends BridgeActivity {
  @Override
  protected void onCreate(Bundle savedInstanceState) {
    // 在 super.onCreate() 之前注册本地 SAF 保存插件，使其进入 bridgeBuilder、随 load() 一并创建。
    // 未引入该插件（旧包）时 Web 侧 capLoad('SaveFile') 返回 null，自动回退分享面板，无副作用。
    registerPlugin(SaveFilePlugin.class);
    super.onCreate(savedInstanceState);
  }
}
