package io.github.lxy.nearcircle.ui;

import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.lifecycle.ViewModelProvider;
import com.google.android.material.button.MaterialButton;
import io.github.lxy.nearcircle.R;

public class LoginActivity extends BaseActivity {
  private boolean register = false;
  private EditText username, password, nickname, server;
  private MaterialButton submit;
  private TextView switchMode, error, heading;
  private AuthViewModel model;

  @Override
  public void onCreate(Bundle state) {
    super.onCreate(state);
    if (session != null) {
      home();
      return;
    }
    if (state != null) register = state.getBoolean("register");
    LinearLayout shell = Ui.column(this);
    ScrollView scroll = new ScrollView(this);
    scroll.setFillViewport(true);
    shell.addView(scroll, new LinearLayout.LayoutParams(-1, -1));
    LinearLayout page = Ui.column(this);
    Ui.pad(page, 28, 24, 28, 20);
    scroll.addView(page);
    TextView brand = Ui.text(this, "NEARCIRCLE  /  近友", 12, Ui.GREEN, true);
    brand.setLetterSpacing(.16f);
    page.addView(brand);
    Ui.gap(page, 36);
    ImageView mark = new ImageView(this);
    mark.setImageResource(R.drawable.ic_launcher);
    mark.setBackground(Ui.shape(Ui.GREEN, 24, this));
    mark.setClipToOutline(true);
    page.addView(mark, Ui.lp(this, 72, 72));
    Ui.gap(page, 24);
    heading = Ui.text(this, register ? "从一声你好开始" : "好朋友，\n慢慢聊。", 36, Ui.INK, true);
    heading.setLineSpacing(Ui.dp(this, 5), 1);
    page.addView(heading);
    Ui.gap(page, 12);
    page.addView(Ui.text(this, "聊天、分享日常，和熟悉的人保持联系。", 14, Ui.MUTED, false));
    Ui.gap(page, 30);
    username = Ui.input(this, "用户名");
    username.setId(io.github.lxy.nearcircle.R.id.login_username);
    username.setInputType(
        InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
    page.addView(username);
    Ui.gap(page, 10);
    nickname = Ui.input(this, "朋友们看到的昵称");
    nickname.setId(io.github.lxy.nearcircle.R.id.login_nickname);
    page.addView(nickname);
    Ui.gap(page, 10);
    password = Ui.input(this, "密码");
    password.setId(io.github.lxy.nearcircle.R.id.login_password);
    password.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
    page.addView(password);
    Ui.gap(page, 8);
    error = Ui.text(this, "", 13, Ui.RED, false);
    error.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
    page.addView(error);
    Ui.gap(page, 8);
    submit = Ui.button(this, register ? "创建账号" : "登录近友");
    page.addView(submit, Ui.lp(this, -1, 56));
    switchMode = Ui.action(this, "");
    page.addView(switchMode);
    switchMode.setOnClickListener(
        v -> {
          register = !register;
          mode();
        });
    Ui.gap(page, 24);
    page.addView(Ui.divider(this));
    Ui.gap(page, 16);
    page.addView(Ui.text(this, "服务地址", 12, Ui.MUTED, true));
    Ui.gap(page, 8);
    server = Ui.input(this, "https://你的服务地址");
    server.setId(io.github.lxy.nearcircle.R.id.login_server);
    server.setText(app.sessions.lastServer());
    server.setTextSize(13);
    server.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
    page.addView(server);
    TextView hint = Ui.text(this, "模拟器使用 10.0.2.2；手机使用电脑的局域网 IP。", 11, Ui.MUTED, false);
    Ui.pad(hint, 0, 8, 0, 0);
    page.addView(hint);
    TextView demo = Ui.action(this, "填入演示账号");
    demo.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
    page.addView(demo);
    demo.setOnClickListener(
        v -> {
          username.setText("lin");
          password.setText("NearCircle2026!");
          register = false;
          mode();
          toast("需服务端以 demo 模式启动，也可使用 zhou 或 xuzhi");
        });
    model = new ViewModelProvider(this).get(AuthViewModel.class);
    model.state.observe(
        this,
        value -> {
          boolean busy = "BUSY".equals(value);
          submit.setEnabled(!busy);
          switchMode.setEnabled(!busy);
          submit.setText(busy ? "正在连接…" : register ? "创建账号" : "登录近友");
          error.setText(busy || "DONE".equals(value) ? "" : value);
          if ("DONE".equals(value)) home();
        });
    submit.setOnClickListener(
        v ->
            model.submit(
                server.getText().toString(),
                username.getText().toString(),
                password.getText().toString(),
                nickname.getText().toString(),
                register));
    mode();
    root(shell);
  }

  private void mode() {
    if (nickname == null) return;
    nickname.setVisibility(register ? View.VISIBLE : View.GONE);
    heading.setText(register ? "从一声你好开始" : "好朋友，\n慢慢聊。");
    submit.setText(register ? "创建账号" : "登录近友");
    switchMode.setText(register ? "已有账号？去登录" : "还没有账号？创建一个");
    error.setText("");
  }

  private void home() {
    startActivity(
        new Intent(this, MainActivity.class)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
    finish();
  }

  @Override
  protected void onSaveInstanceState(Bundle out) {
    super.onSaveInstanceState(out);
    out.putBoolean("register", register);
  }
}
