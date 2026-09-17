package com.jepongdevxyz.idebuild;

import android.content.Context;
import android.content.Intent;
import android.util.AttributeSet;
import android.view.View;
import android.widget.Button;

/** Opens the real DevxyzIDE toolchain inventory/provisioning screen. */
public final class ToolchainManagerButton extends Button {
    public ToolchainManagerButton(Context context) { super(context); initialize(); }
    public ToolchainManagerButton(Context context, AttributeSet attrs) { super(context, attrs); initialize(); }
    public ToolchainManagerButton(Context context, AttributeSet attrs, int defStyleAttr) { super(context, attrs, defStyleAttr); initialize(); }

    private void initialize() {
        setOnClickListener(new OnClickListener() {
            @Override public void onClick(View view) {
                getContext().startActivity(new Intent(getContext(), ToolchainManagerActivity.class));
            }
        });
    }
}
