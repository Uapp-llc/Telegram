/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Bundle;
import android.os.Vibrator;
import android.text.InputFilter;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import org.telegram.localStorage.StorageRepository;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.EditTextEmoji;
import org.telegram.ui.Components.ImageUpdater;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RadialProgressView;
import org.telegram.ui.Components.SizeNotifierFrameLayout;

import java.util.ArrayList;

import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.schedulers.Schedulers;

public class CategoryCreateActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate, ImageUpdater.ImageUpdaterDelegate {

    private View doneButton;
    private EditTextEmoji nameTextView;
    private AlertDialog progressDialog;
    private BackupImageView avatarImage;
    private View avatarOverlay;
    private ImageView avatarEditor;
    private AnimatorSet avatarAnimation;
    private RadialProgressView avatarProgressView;
    private AvatarDrawable avatarDrawable;
    private ImageUpdater imageUpdater;
    private String avatarPath;
    private TLRPC.FileLocation avatar;
    private String nameToSet;
    private String categoryName;

    private DialogsActivity.DialogsActivityDelegate delegate;

    private Boolean showDialogsPicker = true;

    private LinearLayout linearLayout;

    private StorageRepository storage =
            MessagesController.getInstance(currentAccount).localStorage;
    private CompositeDisposable disposables = new CompositeDisposable();

    private static long existingCategoryId = 0;
    private TLRPC.InputFile uploadedAvatar;

    private boolean createAfterUpload;
    private boolean donePressed;

    private final static int done_button = 1;

    public CategoryCreateActivity(Bundle args) {
        super(args);
        avatarDrawable = new AvatarDrawable();
        imageUpdater = new ImageUpdater();
        if (args != null) {
            showDialogsPicker = arguments.getBoolean("showDialogsPicker", true);
        }
    }

    @Override
    public boolean onFragmentCreate() {
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.chatDidCreated);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.chatDidFailCreate);
        if (imageUpdater != null) {
            imageUpdater.parentFragment = this;
            imageUpdater.delegate = this;
        }
        return super.onFragmentCreate();
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.chatDidCreated);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.chatDidFailCreate);
        if (imageUpdater != null) {
            imageUpdater.clear();
        }
        AndroidUtilities.removeAdjustResize(getParentActivity(), classGuid);
        if (nameTextView != null) {
            nameTextView.onDestroy();
        }
        disposables.clear();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (nameTextView != null) {
            nameTextView.onResume();
        }
        AndroidUtilities.requestAdjustResize(getParentActivity(), classGuid);
    }

    @Override
    public void onPause() {
        super.onPause();
        if (nameTextView != null) {
            nameTextView.onPause();
        }
    }

    @Override
    public boolean onBackPressed() {
        if (nameTextView != null && nameTextView.isPopupShowing()) {
            nameTextView.hidePopup(true);
            return false;
        }
        return true;
    }

    @Override
    public View createView(Context context) {
        if (nameTextView != null) {
            nameTextView.onDestroy();
        }

        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);

        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                } else if (id == done_button) {
                    if (donePressed || getParentActivity() == null) {
                        return;
                    }
                    if (nameTextView.length() == 0) {
                        Vibrator v = (Vibrator) getParentActivity().getSystemService(Context.VIBRATOR_SERVICE);
                        if (v != null) {
                            v.vibrate(200);
                        }
                        AndroidUtilities.shakeView(nameTextView, 2, 0);
                        return;
                    } else{
                        categoryName = nameTextView.getText().toString();
                    }
                    donePressed = true;
                    if (imageUpdater.uploadingImage != null) {
                        createAfterUpload = true;
                        progressDialog = new AlertDialog(getParentActivity(), 3);
                        progressDialog.setOnCancelListener(dialog -> {
                            createAfterUpload = false;
                            progressDialog = null;
                            donePressed = false;
                        });
                        progressDialog.show();
                        return;
                    }
                    saveCategory();
                }
            }
        });

        ActionBarMenu menu = actionBar.createMenu();
        doneButton = menu.addItemWithWidth(done_button, R.drawable.ic_done, AndroidUtilities.dp(56));


        actionBar.setTitle(LocaleController.getString("NewCategory", R.string.NewCategory));

        SizeNotifierFrameLayout sizeNotifierFrameLayout = new SizeNotifierFrameLayout(context, SharedConfig.smoothKeyboard) {

            private boolean ignoreLayout;

            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                int widthSize = MeasureSpec.getSize(widthMeasureSpec);
                int heightSize = MeasureSpec.getSize(heightMeasureSpec);

                setMeasuredDimension(widthSize, heightSize);
                heightSize -= getPaddingTop();

                measureChildWithMargins(actionBar, widthMeasureSpec, 0, heightMeasureSpec, 0);

                int keyboardSize = SharedConfig.smoothKeyboard ? 0 : getKeyboardHeight();
                if (keyboardSize > AndroidUtilities.dp(20)) {
                    ignoreLayout = true;
                    nameTextView.hideEmojiView();
                    ignoreLayout = false;
                }

                int childCount = getChildCount();
                for (int i = 0; i < childCount; i++) {
                    View child = getChildAt(i);
                    if (child == null || child.getVisibility() == GONE || child == actionBar) {
                        continue;
                    }
                    if (nameTextView != null && nameTextView.isPopupView(child)) {
                        if (AndroidUtilities.isInMultiwindow || AndroidUtilities.isTablet()) {
                            if (AndroidUtilities.isTablet()) {
                                child.measure(MeasureSpec.makeMeasureSpec(widthSize, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(Math.min(AndroidUtilities.dp(AndroidUtilities.isTablet() ? 200 : 320), heightSize - AndroidUtilities.statusBarHeight + getPaddingTop()), MeasureSpec.EXACTLY));
                            } else {
                                child.measure(MeasureSpec.makeMeasureSpec(widthSize, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(heightSize - AndroidUtilities.statusBarHeight + getPaddingTop(), MeasureSpec.EXACTLY));
                            }
                        } else {
                            child.measure(MeasureSpec.makeMeasureSpec(widthSize, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(child.getLayoutParams().height, MeasureSpec.EXACTLY));
                        }
                    } else {
                        measureChildWithMargins(child, widthMeasureSpec, 0, heightMeasureSpec, 0);
                    }
                }
            }

            @Override
            protected void onLayout(boolean changed, int l, int t, int r, int b) {
                final int count = getChildCount();

                int keyboardSize = SharedConfig.smoothKeyboard ? 0 : getKeyboardHeight();
                int paddingBottom = keyboardSize <= AndroidUtilities.dp(20) && !AndroidUtilities.isInMultiwindow && !AndroidUtilities.isTablet() ? nameTextView.getEmojiPadding() : 0;
                setBottomClip(paddingBottom);

                for (int i = 0; i < count; i++) {
                    final View child = getChildAt(i);
                    if (child.getVisibility() == GONE) {
                        continue;
                    }
                    final LayoutParams lp = (LayoutParams) child.getLayoutParams();

                    final int width = child.getMeasuredWidth();
                    final int height = child.getMeasuredHeight();

                    int childLeft;
                    int childTop;

                    int gravity = lp.gravity;
                    if (gravity == -1) {
                        gravity = Gravity.TOP | Gravity.LEFT;
                    }

                    final int absoluteGravity = gravity & Gravity.HORIZONTAL_GRAVITY_MASK;
                    final int verticalGravity = gravity & Gravity.VERTICAL_GRAVITY_MASK;

                    switch (absoluteGravity & Gravity.HORIZONTAL_GRAVITY_MASK) {
                        case Gravity.CENTER_HORIZONTAL:
                            childLeft = (r - l - width) / 2 + lp.leftMargin - lp.rightMargin;
                            break;
                        case Gravity.RIGHT:
                            childLeft = r - width - lp.rightMargin;
                            break;
                        case Gravity.LEFT:
                        default:
                            childLeft = lp.leftMargin;
                    }

                    switch (verticalGravity) {
                        case Gravity.TOP:
                            childTop = lp.topMargin + getPaddingTop();
                            break;
                        case Gravity.CENTER_VERTICAL:
                            childTop = ((b - paddingBottom) - t - height) / 2 + lp.topMargin - lp.bottomMargin;
                            break;
                        case Gravity.BOTTOM:
                            childTop = ((b - paddingBottom) - t) - height - lp.bottomMargin;
                            break;
                        default:
                            childTop = lp.topMargin;
                    }

                    if (nameTextView != null && nameTextView.isPopupView(child)) {
                        if (AndroidUtilities.isTablet()) {
                            childTop = getMeasuredHeight() - child.getMeasuredHeight();
                        } else {
                            childTop = getMeasuredHeight() + keyboardSize - child.getMeasuredHeight();
                        }
                    }
                    child.layout(childLeft, childTop, childLeft + width, childTop + height);
                }

                notifyHeightChanged();
            }

            @Override
            public void requestLayout() {
                if (ignoreLayout) {
                    return;
                }
                super.requestLayout();
            }
        };
        sizeNotifierFrameLayout.setOnTouchListener((v, event) -> true);
        fragmentView = sizeNotifierFrameLayout;
        fragmentView.setTag(Theme.key_windowBackgroundWhite);
        fragmentView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));

        linearLayout = new LinearLayout(context);
        linearLayout.setOrientation(LinearLayout.VERTICAL);
        sizeNotifierFrameLayout.addView(linearLayout, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        FrameLayout frameLayout = new FrameLayout(context);
        linearLayout.addView(frameLayout, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        avatarImage = new BackupImageView(context) {
            @Override
            public void invalidate() {
                if (avatarOverlay != null) {
                    avatarOverlay.invalidate();
                }
                super.invalidate();
            }

            @Override
            public void invalidate(int l, int t, int r, int b) {
                if (avatarOverlay != null) {
                    avatarOverlay.invalidate();
                }
                super.invalidate(l, t, r, b);
            }
        };
        avatarImage.setRoundRadius(AndroidUtilities.dp(32));
        avatarDrawable.setInfo(5, null, null);
        avatarImage.setImageDrawable(avatarDrawable);
        frameLayout.addView(avatarImage, LayoutHelper.createFrame(64, 64, Gravity.TOP | (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT), LocaleController.isRTL ? 0 : 16, 12, LocaleController.isRTL ? 16 : 0, 12));

        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(0x55000000);

        avatarOverlay = new View(context) {
            @Override
            protected void onDraw(Canvas canvas) {
                if (avatarImage != null && avatarImage.getImageReceiver().hasNotThumb()) {
                    paint.setAlpha((int) (0x55 * avatarImage.getImageReceiver().getCurrentAlpha()));
                    canvas.drawCircle(getMeasuredWidth() / 2, getMeasuredHeight() / 2, AndroidUtilities.dp(32), paint);
                }
            }
        };
        frameLayout.addView(avatarOverlay, LayoutHelper.createFrame(64, 64, Gravity.TOP | (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT), LocaleController.isRTL ? 0 : 16, 12, LocaleController.isRTL ? 16 : 0, 12));
        avatarOverlay.setOnClickListener(view -> imageUpdater.openMenu(avatar != null, () -> {
            avatar = null;
            uploadedAvatar = null;
            showAvatarProgress(false, true);
            avatarImage.setImage(null, null, avatarDrawable, null);
        }));

        avatarEditor = new ImageView(context) {
            @Override
            public void invalidate(int l, int t, int r, int b) {
                super.invalidate(l, t, r, b);
                avatarOverlay.invalidate();
            }

            @Override
            public void invalidate() {
                super.invalidate();
                avatarOverlay.invalidate();
            }
        };
        avatarEditor.setScaleType(ImageView.ScaleType.CENTER);
        avatarEditor.setImageResource(R.drawable.menu_camera_av);
        avatarEditor.setEnabled(false);
        avatarEditor.setClickable(false);
        frameLayout.addView(avatarEditor, LayoutHelper.createFrame(64, 64, Gravity.TOP | (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT), LocaleController.isRTL ? 0 : 16, 12, LocaleController.isRTL ? 16 : 0, 12));

        avatarProgressView = new RadialProgressView(context);
        avatarProgressView.setSize(AndroidUtilities.dp(30));
        avatarProgressView.setProgressColor(0xffffffff);
        frameLayout.addView(avatarProgressView, LayoutHelper.createFrame(64, 64, Gravity.TOP | (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT), LocaleController.isRTL ? 0 : 16, 12, LocaleController.isRTL ? 16 : 0, 12));

        showAvatarProgress(false, false);

        nameTextView = new EditTextEmoji(context, sizeNotifierFrameLayout, this, EditTextEmoji.STYLE_FRAGMENT);
        nameTextView.setHint(LocaleController.getString("EnterCategoryName", R.string.EnterCategoryName));
        if (nameToSet != null) {
            nameTextView.setText(nameToSet);
            nameToSet = null;
        }
        InputFilter[] inputFilters = new InputFilter[1];
        inputFilters[0] = new InputFilter.LengthFilter(100);
        nameTextView.setFilters(inputFilters);
        frameLayout.addView(nameTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL, LocaleController.isRTL ? 5 : 96, 0, LocaleController.isRTL ? 96 : 5, 0));

        return fragmentView;
    }

    @Override
    public void didUploadPhoto(final TLRPC.InputFile file, final TLRPC.PhotoSize bigSize, final TLRPC.PhotoSize smallSize) {
        AndroidUtilities.runOnUIThread(() -> {
            if (file != null) {
                uploadedAvatar = file;
                avatarPath = imageUpdater.picturePath;
                if (createAfterUpload) {
                    try {
                        if (progressDialog != null && progressDialog.isShowing()) {
                            progressDialog.dismiss();
                            progressDialog = null;
                        }
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                    donePressed = false;
                    doneButton.performClick();
                }
                showAvatarProgress(false, true);
            } else {
                avatar = smallSize.location;
                avatarImage.setImage(ImageLocation.getForLocal(avatar), "50_50", avatarDrawable, null);
                showAvatarProgress(true, false);
            }
        });
    }

    @Override
    public String getInitialSearchString() {
        return nameTextView.getText().toString();
    }

    private void showAvatarProgress(boolean show, boolean animated) {
        if (avatarEditor == null) {
            return;
        }
        if (avatarAnimation != null) {
            avatarAnimation.cancel();
            avatarAnimation = null;
        }
        if (animated) {
            avatarAnimation = new AnimatorSet();
            if (show) {
                avatarProgressView.setVisibility(View.VISIBLE);

                avatarAnimation.playTogether(ObjectAnimator.ofFloat(avatarEditor, View.ALPHA, 0.0f),
                        ObjectAnimator.ofFloat(avatarProgressView, View.ALPHA, 1.0f));
            } else {
                avatarEditor.setVisibility(View.VISIBLE);

                avatarAnimation.playTogether(ObjectAnimator.ofFloat(avatarEditor, View.ALPHA, 1.0f),
                        ObjectAnimator.ofFloat(avatarProgressView, View.ALPHA, 0.0f));
            }
            avatarAnimation.setDuration(180);
            avatarAnimation.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    if (avatarAnimation == null || avatarEditor == null) {
                        return;
                    }
                    if (show) {
                        avatarEditor.setVisibility(View.INVISIBLE);
                    } else {
                        avatarProgressView.setVisibility(View.INVISIBLE);
                    }
                    avatarAnimation = null;
                }

                @Override
                public void onAnimationCancel(Animator animation) {
                    avatarAnimation = null;
                }
            });
            avatarAnimation.start();
        } else {
            if (show) {
                avatarEditor.setAlpha(1.0f);
                avatarEditor.setVisibility(View.INVISIBLE);
                avatarProgressView.setAlpha(1.0f);
                avatarProgressView.setVisibility(View.VISIBLE);
            } else {
                avatarEditor.setAlpha(1.0f);
                avatarEditor.setVisibility(View.VISIBLE);
                avatarProgressView.setAlpha(0.0f);
                avatarProgressView.setVisibility(View.INVISIBLE);
            }
        }
    }

    @Override
    public void onActivityResultFragment(int requestCode, int resultCode, Intent data) {
        if (imageUpdater != null) {
            imageUpdater.onActivityResult(requestCode, resultCode, data);
        }
    }

    @Override
    public void saveSelfArgs(Bundle args) {
        if (imageUpdater != null && imageUpdater.currentPicturePath != null) {
            args.putString("path", imageUpdater.currentPicturePath);
        }
        if (nameTextView != null) {
            String text = nameTextView.getText().toString();
            if (text != null && text.length() != 0) {
                args.putString("nameTextView", text);
            }
        }
    }

    @Override
    public void restoreSelfArgs(Bundle args) {
        if (imageUpdater != null) {
            imageUpdater.currentPicturePath = args.getString("path");
        }
        String text = args.getString("nameTextView");
        if (text != null) {
            if (nameTextView != null) {
                nameTextView.setText(text);
            } else {
                nameToSet = text;
            }
        }
    }

    @Override
    public void onTransitionAnimationEnd(boolean isOpen, boolean backward) {
        if (isOpen) {
            nameTextView.requestFocus();
            nameTextView.openKeyboard();
        }
    }

    public void setDelegate(DialogsActivity.DialogsActivityDelegate dialogsActivityDelegate) {
        delegate = dialogsActivityDelegate;
    }

    private void saveCategory() {
        if (categoryName == null || categoryName.isEmpty()) {
            return;
        }
        if(avatarPath == null){
            avatarPath = "";
        }
        long userId = UserConfig.getInstance(currentAccount).getClientUserId();
        long pinNum = getMessagesController().getLastPinnedNum();
        disposables.add(
                storage.createCategory(0, userId, pinNum, categoryName, avatarPath)
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(() -> {
                                    Log.d("Local Storage", "Category " + categoryName + " saved");
                                    getMessagesController().reloadDbCategories();
                                    getCategoryId(userId);
                                }, Throwable::printStackTrace
                        )
        );
    }

    private void getCategoryId(long userId) {
        disposables.add(
                storage.getUserLastCategoryId(userId)
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(id -> {
                                    existingCategoryId = id;
                                    Log.d("Local Storage", "Last Category Id" + id + " saved");
                                    if(showDialogsPicker){
                                        showDialogsPicker();
                                    } else{
                                        processDelegateCallToAddDialogs();
                                        finishFragment();
                                    }
                                }, Throwable::printStackTrace
                        )
        );
    }

    private void processDelegateCallToAddDialogs(){
        ArrayList<Long> categoryId = new ArrayList<>();
        categoryId.add(existingCategoryId);
        delegate.didSelectDialogs(null, categoryId, null, false);
    }

    private void showDialogsPicker() {
        Bundle args = new Bundle();
        args.putBoolean("onlySelect", true);
        args.putInt("dialogsType", 9);
        args.putString("categoryName", categoryName);
        args.putLong("existingCategoryId", existingCategoryId);
        presentFragment(new DialogsActivity(args), true);
    }

    @Override
    public ThemeDescription[] getThemeDescriptions() {
        return new ThemeDescription[]{
                new ThemeDescription(fragmentView, ThemeDescription.FLAG_BACKGROUND | ThemeDescription.FLAG_CHECKTAG, null, null, null, null, Theme.key_windowBackgroundWhite),
                new ThemeDescription(fragmentView, ThemeDescription.FLAG_BACKGROUND | ThemeDescription.FLAG_CHECKTAG, null, null, null, null, Theme.key_windowBackgroundGray),

                new ThemeDescription(actionBar, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_actionBarDefault),
                new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_ITEMSCOLOR, null, null, null, null, Theme.key_actionBarDefaultIcon),
                new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_TITLECOLOR, null, null, null, null, Theme.key_actionBarDefaultTitle),
                new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SELECTORCOLOR, null, null, null, null, Theme.key_actionBarDefaultSelector),

                new ThemeDescription(nameTextView, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteBlackText),
                new ThemeDescription(nameTextView, ThemeDescription.FLAG_HINTTEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteHintText),
                new ThemeDescription(nameTextView, ThemeDescription.FLAG_BACKGROUNDFILTER, null, null, null, null, Theme.key_windowBackgroundWhiteInputField),
                new ThemeDescription(nameTextView, ThemeDescription.FLAG_BACKGROUNDFILTER | ThemeDescription.FLAG_DRAWABLESELECTEDSTATE, null, null, null, null, Theme.key_windowBackgroundWhiteInputFieldActivated),
        };
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {

    }
}
