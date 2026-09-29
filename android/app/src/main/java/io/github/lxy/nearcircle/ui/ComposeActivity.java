package io.github.lxy.nearcircle.ui;

import android.os.Bundle;
import android.text.InputFilter;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.lifecycle.ViewModelProvider;
import com.google.android.material.button.MaterialButton;

public class ComposeActivity extends BaseActivity {
  private ComposeViewModel model;
  private EditText editor;
  private final ActivityResultLauncher<String[]> picker =
      registerForActivityResult(
          new ActivityResultContracts.OpenDocument(),
          uri -> {
            if (uri != null) model.image(uri);
          });

  @Override
  public void onCreate(Bundle saved) {
    super.onCreate(saved);
    if (!requireSession()) return;
    model = new ViewModelProvider(this).get(ComposeViewModel.class);
    LinearLayout shell = Ui.column(this);
    LinearLayout bar = Ui.row(this);
    Ui.pad(bar, 12, 8, 18, 8);
    TextView cancel = Ui.action(this, "取消");
    cancel.setTextColor(Ui.MUTED);
    bar.addView(cancel);
    cancel.setOnClickListener(v -> finish());
    TextView title = Ui.text(this, "记录此刻", 18, Ui.INK, true);
    title.setGravity(Gravity.CENTER);
    bar.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
    MaterialButton publish = Ui.button(this, "发布");
    bar.addView(publish, Ui.lp(this, 76, 50));
    shell.addView(bar);
    shell.addView(Ui.divider(this));
    ScrollView scroll = new ScrollView(this);
    LinearLayout page = Ui.column(this);
    Ui.pad(page, 24, 24, 24, 24);
    scroll.addView(page);
    shell.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
    LinearLayout author = Ui.row(this);
    author.addView(Ui.avatar(this, session.displayName, session.userId, 44));
    LinearLayout labels = Ui.column(this);
    Ui.pad(labels, 12, 0, 0, 0);
    labels.addView(Ui.text(this, session.displayName, 16, Ui.INK, true));
    Ui.gap(labels, 5);
    labels.addView(Ui.text(this, "分享给好友", 12, Ui.MUTED, false));
    author.addView(labels);
    page.addView(author);
    Ui.gap(page, 24);
    editor = Ui.input(this, "这一刻，想和朋友分享什么？");
    editor.setId(io.github.lxy.nearcircle.R.id.post_body);
    editor.setSingleLine(false);
    editor.setGravity(Gravity.TOP);
    editor.setMinLines(6);
    editor.setBackgroundColor(Ui.BG);
    Ui.pad(editor, 0, 0, 0, 0);
    editor.setFilters(new InputFilter[] {new InputFilter.LengthFilter(2000)});
    page.addView(editor, Ui.lp(this, -1, -2));
    Ui.gap(page, 16);
    ImageView preview = new ImageView(this);
    preview.setScaleType(ImageView.ScaleType.CENTER_CROP);
    preview.setBackground(Ui.shape(Ui.PALE, 20, this));
    preview.setClipToOutline(true);
    preview.setContentDescription("待发布的图片");
    page.addView(preview, Ui.lp(this, -1, 240));
    preview.setVisibility(View.GONE);
    TextView photo = Ui.action(this, "＋  添加一张图片");
    photo.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
    page.addView(photo);
    photo.setOnClickListener(v -> picker.launch(new String[] {"image/*"}));
    TextView remove = Ui.action(this, "移除图片");
    remove.setTextColor(Ui.MUTED);
    page.addView(remove);
    remove.setVisibility(View.GONE);
    remove.setOnClickListener(v -> model.image(null));
    Ui.gap(page, 28);
    page.addView(Ui.divider(this));
    Ui.gap(page, 18);
    page.addView(Ui.text(this, "可见范围", 12, Ui.MUTED, true));
    Ui.gap(page, 10);
    page.addView(Ui.text(this, "我的好友", 16, Ui.GREEN, true));
    Ui.gap(page, 8);
    page.addView(Ui.text(this, "只有你和已通过好友申请的人可以查看。", 12, Ui.MUTED, false));
    Ui.gap(page, 20);
    TextView error = Ui.text(this, "", 13, Ui.RED, false);
    error.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
    page.addView(error);
    model.preview.observe(
        this,
        bitmap -> {
          preview.setImageBitmap(bitmap);
          preview.setVisibility(bitmap == null ? View.GONE : View.VISIBLE);
          remove.setVisibility(bitmap == null ? View.GONE : View.VISIBLE);
          photo.setText(bitmap == null ? "＋  添加一张图片" : "更换图片");
        });
    model.state.observe(
        this,
        state -> {
          boolean busy = "BUSY".equals(state), photoBusy = "PHOTO".equals(state);
          publish.setEnabled(!busy && !photoBusy);
          photo.setEnabled(!busy && !photoBusy);
          remove.setEnabled(!busy);
          cancel.setEnabled(!busy);
          editor.setEnabled(!busy);
          publish.setText(busy ? "发布中" : "发布");
          error.setText(busy ? "正在发布…" : photoBusy ? "正在处理图片…" : "DONE".equals(state) ? "" : state);
          if ("DONE".equals(state)) {
            toast("已发布到朋友圈");
            setResult(RESULT_OK);
            finish();
          }
        });
    publish.setOnClickListener(v -> model.publish(session, editor.getText().toString()));
    root(shell);
  }
}
