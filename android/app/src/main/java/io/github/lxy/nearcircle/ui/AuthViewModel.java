package io.github.lxy.nearcircle.ui;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.MutableLiveData;
import com.google.gson.JsonObject;
import io.github.lxy.nearcircle.NearApp;
import io.github.lxy.nearcircle.net.ApiClient;

public class AuthViewModel extends AndroidViewModel {
  public final MutableLiveData<String> state = new MutableLiveData<>("");

  public AuthViewModel(@NonNull Application app) {
    super(app);
  }

  public void submit(
      String base, String username, String password, String nickname, boolean register) {
    if ("BUSY".equals(state.getValue())) return;
    if (username.trim().isEmpty() || password.isEmpty()) {
      state.setValue("请输入账号和密码");
      return;
    }
    state.setValue("BUSY");
    NearApp app = getApplication();
    app.io.execute(
        () -> {
          try {
            String normalized = ApiClient.normalizeBase(base);
            JsonObject body = new JsonObject();
            body.addProperty("username", username.trim());
            body.addProperty("password", password);
            if (register) body.addProperty("display_name", nickname.trim());
            JsonObject result =
                new ApiClient(normalized, "")
                    .post(register ? "/auth/register" : "/auth/login", body);
            app.sessions.save(
                normalized, result.get("token").getAsString(), result.getAsJsonObject("user"));
            state.postValue("DONE");
          } catch (Exception e) {
            state.postValue(
                e instanceof ApiClient.ApiException || e instanceof IllegalArgumentException
                    ? e.getMessage()
                    : "连接失败，请确认服务已启动、地址正确且处于同一网络");
          }
        });
  }
}
