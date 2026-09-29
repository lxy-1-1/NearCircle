package io.github.lxy.nearcircle.ui;

import static org.junit.Assert.*;

import android.net.Uri;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import io.github.lxy.nearcircle.NearApp;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class ComposeRegressionTest {
  @Test
  public void removingImageWhileProcessingAllowsAnotherSelection() {
    NearApp app = ApplicationProvider.getApplicationContext();
    ComposeViewModel model = new ComposeViewModel(app);
    InstrumentationRegistry.getInstrumentation()
        .runOnMainSync(
            () -> {
              // Both user actions must run before any asynchronous completion is dispatched.
              model.image(Uri.parse("content://nearcircle.invalid/pending"));
              assertEquals("PHOTO", model.state.getValue());
              model.image(null);
              assertEquals("", model.state.getValue());
              assertNull(model.preview.getValue());
            });
    InstrumentationRegistry.getInstrumentation().waitForIdleSync();
  }
}
