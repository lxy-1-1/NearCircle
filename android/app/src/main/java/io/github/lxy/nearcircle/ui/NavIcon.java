package io.github.lxy.nearcircle.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;

/** Small vector icons, rendered at native density; no image assets or font glyph dependencies. */
public final class NavIcon extends View {
  private final int kind;
  private boolean active;
  private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Path tail = new Path();

  public NavIcon(Context c) {
    this(c, 0);
  }

  public NavIcon(Context c, int kind) {
    super(c);
    this.kind = kind;
    setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
  }

  public void active(boolean value) {
    active = value;
    invalidate();
  }

  @Override
  protected void onDraw(Canvas c) {
    super.onDraw(c);
    c.save();
    c.scale(getWidth() / 28f, getHeight() / 28f);
    p.setColor(active ? Ui.GREEN : Ui.MUTED);
    p.setStyle(Paint.Style.STROKE);
    p.setStrokeWidth(1.8f);
    p.setStrokeCap(Paint.Cap.ROUND);
    p.setStrokeJoin(Paint.Join.ROUND);
    if (kind == 0) {
      c.drawRoundRect(4, 4, 24, 20, 6, 6, p);
      tail.reset();
      tail.moveTo(8, 20);
      tail.lineTo(8, 24);
      tail.lineTo(13, 20);
      c.drawPath(tail, p);
      c.drawLine(9, 11, 19, 11, p);
      c.drawLine(9, 15, 15, 15, p);
    } else if (kind == 1) {
      c.drawCircle(11, 9, 4, p);
      c.drawArc(3, 16, 19, 29, 185, 170, false, p);
      c.drawArc(16, 5, 24, 13, 260, 170, false, p);
      c.drawArc(17, 16, 26, 27, 260, 80, false, p);
    } else if (kind == 2) {
      c.drawCircle(14, 14, 10, p);
      c.drawCircle(14, 14, 3, p);
      c.drawLine(14, 4, 14, 8, p);
      c.drawLine(24, 14, 20, 14, p);
      c.drawLine(14, 24, 14, 20, p);
      c.drawLine(4, 14, 8, 14, p);
    } else {
      c.drawCircle(14, 9, 4, p);
      c.drawRoundRect(5, 17, 23, 25, 7, 7, p);
    }
    c.restore();
  }
}
