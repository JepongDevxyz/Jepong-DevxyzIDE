package com.jepongdevxyz.idebuild;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;

public final class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        TextView view = new TextView(this);
        view.setText("DevxyzIDE CI environment verified build");
        view.setTextSize(20f);
        view.setPadding(24, 24, 24, 24);
        setContentView(view);
    }
}
