package com.jepongdevxyz.idebuild;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.Button;

import com.jepongdevxyz.idebuild.core.SafeZip;

/**
 * Keeps the Import action clickable while MainActivity marks an import busy.
 * During that busy window a tap requests cancellation instead of opening a
 * second picker. MainActivity can keep its normal Button contract.
 */
public final class CancelableImportButton extends Button {
    private boolean importBusy;
    private OnClickListener delegate;

    public CancelableImportButton(Context context) {
        super(context);
        installDispatcher();
    }

    public CancelableImportButton(Context context, AttributeSet attrs) {
        super(context, attrs);
        installDispatcher();
    }

    public CancelableImportButton(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        installDispatcher();
    }

    private void installDispatcher() {
        super.setOnClickListener(new OnClickListener() {
            @Override public void onClick(View view) {
                if (importBusy) {
                    SafeZip.cancelPreparedOrActiveProjectExtraction();
                    setText(R.string.canceling_import);
                    return;
                }
                OnClickListener listener = delegate;
                if (listener != null) listener.onClick(view);
            }
        });
    }

    @Override public void setOnClickListener(OnClickListener listener) {
        delegate = listener;
    }

    @Override public void setEnabled(boolean enabled) {
        if (enabled) {
            importBusy = false;
            SafeZip.clearProjectExtractionRequest();
            super.setEnabled(true);
            setText(R.string.import_zip);
        } else {
            importBusy = true;
            SafeZip.prepareProjectExtraction();
            super.setEnabled(true);
            setText(R.string.cancel_import);
        }
    }
}
