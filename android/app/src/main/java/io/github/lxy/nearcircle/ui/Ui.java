package io.github.lxy.nearcircle.ui;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.google.android.material.button.MaterialButton;

public final class Ui {
  public static final int BG = Color.rgb(247, 248, 244),
      INK = Color.rgb(29, 43, 37),
      GREEN = Color.rgb(36, 92, 77),
      MUTED = Color.rgb(114, 124, 117),
      LINE = Color.rgb(229, 233, 224),
      PALE = Color.rgb(231, 237, 220),
      WHITE = Color.WHITE,
      RED = Color.rgb(172, 70, 60);

  public static int dp(Context c, float v) {
    return Math.round(v * c.getResources().getDisplayMetrics().density);
  }

  public static GradientDrawable shape(int color, int radius, Context c) {
    GradientDrawable d = new GradientDrawable();
    d.setColor(color);
    d.setCornerRadius(dp(c, radius));
    return d;
  }

  public static LinearLayout column(Context c) {
    LinearLayout v = new LinearLayout(c);
    v.setOrientation(LinearLayout.VERTICAL);
    return v;
  }

  public static LinearLayout row(Context c) {
    LinearLayout v = new LinearLayout(c);
    v.setGravity(Gravity.CENTER_VERTICAL);
    return v;
  }

  public static TextView text(Context c, String s, int size, int color, boolean bold) {
    TextView v = new TextView(c);
    v.setText(s);
    v.setTextSize(size);
    v.setTextColor(color);
    v.setIncludeFontPadding(false);
    if (bold) v.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
    return v;
  }

  public static void pad(View v, int l, int t, int r, int b) {
    v.setPadding(
        dp(v.getContext(), l), dp(v.getContext(), t), dp(v.getContext(), r), dp(v.getContext(), b));
  }

  public static LinearLayout.LayoutParams lp(Context c, int w, int h) {
    return new LinearLayout.LayoutParams(w < 0 ? w : dp(c, w), h < 0 ? h : dp(c, h));
  }

  public static void gap(LinearLayout p, int h) {
    p.addView(new View(p.getContext()), lp(p.getContext(), 1, h));
  }

  public static View divider(Context c) {
    View d = new View(c);
    d.setBackgroundColor(LINE);
    d.setLayoutParams(new LinearLayout.LayoutParams(-1, dp(c, 1)));
    return d;
  }

  public static MaterialButton button(Context c, String s) {
    MaterialButton b = new MaterialButton(c);
    b.setText(s);
    b.setTextSize(15);
    b.setAllCaps(false);
    b.setCornerRadius(dp(c, 16));
    b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(GREEN));
    b.setTextColor(WHITE);
    b.setMinHeight(dp(c, 48));
    return b;
  }

  public static TextView action(Context c, String label) {
    TextView v = text(c, label, 14, GREEN, true);
    pad(v, 12, 14, 12, 14);
    v.setGravity(Gravity.CENTER);
    v.setMinHeight(dp(c, 48));
    v.setClickable(true);
    return v;
  }

  public static EditText input(Context c, String hint) {
    EditText v = new EditText(c);
    v.setHint(hint);
    v.setTextColor(INK);
    v.setHintTextColor(MUTED);
    v.setTextSize(16);
    v.setSingleLine(true);
    v.setBackground(shape(WHITE, 14, c));
    pad(v, 16, 16, 16, 16);
    v.setLayoutParams(lp(c, -1, -2));
    return v;
  }

  public static TextView avatar(Context c, String name, long id, int size) {
    int[] colors = {0xffe5eddc, 0xffede4db, 0xffdce9eb, 0xffe8e1ec, 0xffecebd7};
    TextView t = text(c, name.isEmpty() ? "友" : name.substring(0, 1), size / 3, INK, true);
    t.setGravity(Gravity.CENTER);
    t.setBackground(shape(colors[(int) Math.floorMod(id, colors.length)], size / 3, c));
    t.setLayoutParams(lp(c, size, size));
    t.setContentDescription(name + "的头像");
    return t;
  }

  public static String time(long value) {
    return new java.text.SimpleDateFormat("HH:mm", java.util.Locale.CHINA)
        .format(new java.util.Date(value));
  }

  public static String date(long value) {
    return new java.text.SimpleDateFormat("MM月dd日 HH:mm", java.util.Locale.CHINA)
        .format(new java.util.Date(value));
  }

  private Ui() {}
}
