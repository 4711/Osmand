package net.osmand.plus.widgets;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Typeface;
import android.os.Build;
import android.support.v7.internal.text.AllCapsTransformationMethod;
import android.util.AttributeSet;
import android.widget.TextView;

import net.osmand.plus.R;
import net.osmand.plus.helpers.FontCache;

/**
 * Created by Alexey Pelykh on 30.01.2015.
 */
public class TextViewEx extends TextView {
	public TextViewEx(Context context) {
		super(context);
	}

	public TextViewEx(Context context, AttributeSet attrs) {
		super(context, attrs);

		parseAttributes(this, attrs, 0, 0);
	}

	public TextViewEx(Context context, AttributeSet attrs, int defStyleAttr) {
		super(context, attrs, defStyleAttr);

		parseAttributes(this, attrs, defStyleAttr, 0);
	}

	@TargetApi(21)
	public TextViewEx(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
		super(context, attrs, defStyleAttr, defStyleRes);

		parseAttributes(this, attrs, defStyleAttr, defStyleRes);
	}

	/*internal*/ static void parseAttributes(TextView target, AttributeSet attrs, int defStyleAttr,
											 int defStyleRes) {
		if (attrs == null) {
			return;
		}

		TypedArray resolvedAttrs = target.getContext().getTheme().obtainStyledAttributes(attrs,
				R.styleable.TextViewEx, defStyleAttr, defStyleRes);
		applyAttributes(resolvedAttrs, target);
		resolvedAttrs.recycle();
	}

	private static void applyAttributes(TypedArray resolvedAttributes, TextView target) {
		applyAttribute_typeface(resolvedAttributes, target);
		applyAttribute_textAllCapsCompat(resolvedAttributes, target);
	}

	/*internal*/ static void applyAttribute_typeface(TypedArray resolvedAttributes,
													 TextView target) {
		if (!resolvedAttributes.hasValue(R.styleable.TextViewEx_typeface)
				|| target.isInEditMode()) {
			return;
		}

		String typefaceName = resolvedAttributes.getString(R.styleable.TextViewEx_typeface);
		Typeface typeface = FontCache.getFont(target.getContext(), typefaceName);
		if (typeface != null)
			target.setTypeface(typeface);
	}

	public static void setAllCapsCompat(TextView target, boolean allCaps) {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
			target.setAllCaps(allCaps);
			return;
		}

		if (allCaps) {
			target.setTransformationMethod(new AllCapsTransformationMethod(target.getContext()));
		} else {
			target.setTransformationMethod(null);
		}
	}

	public void setAllCapsCompat(boolean allCaps) {
		setAllCapsCompat(this, allCaps);
	}

	/*internal*/ static void applyAttribute_textAllCapsCompat(TypedArray resolvedAttributes,
														TextView target) {
		if (!resolvedAttributes.hasValue(R.styleable.TextViewEx_textAllCapsCompat)) {
			return;
		}

		boolean textAllCaps = resolvedAttributes.getBoolean(
				R.styleable.TextViewEx_textAllCapsCompat, false);
		if (!textAllCaps) {
			return;
		}
		setAllCapsCompat(target, true);
	}
}
