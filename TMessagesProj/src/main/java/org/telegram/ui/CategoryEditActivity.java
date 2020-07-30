package org.telegram.ui;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
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
import android.widget.TextView;

import org.telegram.localStorage.StorageRepository;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.NotificationsController;
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
import org.telegram.ui.Cells.ShadowSectionCell;
import org.telegram.ui.Cells.TextCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextDetailCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.Category;
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

public class CategoryEditActivity extends BaseFragment implements ImageUpdater.ImageUpdaterDelegate, NotificationCenter.NotificationCenterDelegate {

    private View doneButton;


    private AlertDialog progressDialog;

    private LinearLayout avatarContainer;
    private BackupImageView avatarImage;
    private View avatarOverlay;
    private ImageView avatarEditor;
    private AnimatorSet avatarAnimation;
    private RadialProgressView avatarProgressView;
    private AvatarDrawable avatarDrawable;
    private ImageUpdater imageUpdater;
    private EditTextEmoji nameTextView;

    private String oldAvatarPath;
    private String newAvatarPath;
    private String categoryName;

    private LinearLayout settingsContainer;

    private LinearLayout typeEditContainer;
    private ShadowSectionCell settingsTopSectionCell;
    private TextCheckCell notificationsCell;
    private ShadowSectionCell settingsSectionCell;

    private FrameLayout deleteContainer;
    private TextSettingsCell deleteCell;
    private ShadowSectionCell deleteInfoCell;

    private TLRPC.FileLocation avatar;
    private long categoryId;
    private TLRPC.InputFile uploadedAvatar;
    private Category category;

    private boolean createAfterUpload;
    private boolean donePressed;

    private final static int done_button = 1;

    private StorageRepository storage =
            MessagesController.getInstance(currentAccount).localStorage;

    private CompositeDisposable disposables = new CompositeDisposable();

    float touchPositionX = 0;
    float touchPositionY = 0;

    private CategoryEditDelegate delegate;

    public CategoryEditActivity(Bundle args) {
        super(args);
        avatarDrawable = new AvatarDrawable();
        imageUpdater = new ImageUpdater();
        categoryId = args.getLong("categoryId", 0);
    }

    @Override
    public boolean onFragmentCreate() {
        category = MessagesController.getInstance(currentAccount).categories_dict.get(categoryId);
        oldAvatarPath = category.getAvatar();
        newAvatarPath = oldAvatarPath;
        if (category == null) {
            return false;
        }

        imageUpdater.parentFragment = this;
        imageUpdater.delegate = this;
        return super.onFragmentCreate();
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
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
            nameTextView.getEditText().requestFocus();
        }
        AndroidUtilities.requestAdjustResize(getParentActivity(), classGuid);
        notificationsCell.setTextAndCheck(LocaleController.getString("Notifications", R.string.Notifications), getNotificationsController().isGlobalNotificationsEnabled(NotificationsController.TYPE_CATEGORY, categoryId), true);
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

    @SuppressLint("ClickableViewAccessibility")
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
                    processDone();
                }
            }
        });

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
        fragmentView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));

        ScrollView scrollView = new ScrollView(context);
        scrollView.setFillViewport(true);
        sizeNotifierFrameLayout.addView(scrollView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        LinearLayout linearLayout1 = new LinearLayout(context);
        scrollView.addView(linearLayout1, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        linearLayout1.setOrientation(LinearLayout.VERTICAL);

        actionBar.setTitle(LocaleController.getString("ChannelEdit", R.string.ChannelEdit));

        avatarContainer = new LinearLayout(context);
        avatarContainer.setOrientation(LinearLayout.VERTICAL);
        avatarContainer.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        linearLayout1.addView(avatarContainer, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        FrameLayout frameLayout = new FrameLayout(context);
        avatarContainer.addView(frameLayout, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

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
        frameLayout.addView(avatarImage, LayoutHelper.createFrame(64, 64, Gravity.TOP | (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT), LocaleController.isRTL ? 0 : 16, 12, LocaleController.isRTL ? 16 : 0, 12));

        avatarDrawable.setInfo(5, null, null);

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
        avatarOverlay.setOnClickListener(view -> imageUpdater.openMenu(!newAvatarPath.isEmpty(), () -> {
            avatar = null;
            uploadedAvatar = null;
            newAvatarPath = "";
            showAvatarProgress(false, true);
            avatarImage.setImage(null, null, avatarDrawable);
        }));
        avatarOverlay.setContentDescription(LocaleController.getString("ChoosePhoto", R.string.ChoosePhoto));

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
        nameTextView.setHint(LocaleController.getString("EnterChannelName", R.string.EnterCategoryName));
        nameTextView.setFocusable(nameTextView.isEnabled());
        InputFilter[] inputFilters = new InputFilter[1];
        inputFilters[0] = new InputFilter.LengthFilter(128);
        nameTextView.setFilters(inputFilters);
        frameLayout.addView(nameTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL, LocaleController.isRTL ? 5 : 96, 0, LocaleController.isRTL ? 96 : 5, 0));

        settingsContainer = new LinearLayout(context);
        settingsContainer.setOrientation(LinearLayout.VERTICAL);
        settingsContainer.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        linearLayout1.addView(settingsContainer, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        settingsTopSectionCell = new ShadowSectionCell(context);
        linearLayout1.addView(settingsTopSectionCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        typeEditContainer = new LinearLayout(context);
        typeEditContainer.setOrientation(LinearLayout.VERTICAL);
        typeEditContainer.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        linearLayout1.addView(typeEditContainer, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        notificationsCell = new TextCheckCell(context);
        notificationsCell.setBackgroundDrawable(Theme.getSelectorDrawable(false));
        typeEditContainer.addView(notificationsCell, LayoutHelper.createLinear(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        notificationsCell.setOnTouchListener((view, event) -> {
            touchPositionX = event.getX();
            touchPositionY = event.getY();
            return false;
        });
        notificationsCell.setOnClickListener(v -> {
            TextCheckCell checkCell = (TextCheckCell) v;
            boolean enabled = NotificationsController.getInstance(currentAccount).isGlobalNotificationsEnabled(NotificationsController.TYPE_CATEGORY, categoryId);
            if (LocaleController.isRTL && touchPositionX <= AndroidUtilities.dp(76) || !LocaleController.isRTL && touchPositionX >= v.getMeasuredWidth() - AndroidUtilities.dp(76)) {
                NotificationsController.getInstance(currentAccount).setGlobalNotificationsEnabled(NotificationsController.TYPE_CATEGORY, !enabled ? 0 : Integer.MAX_VALUE, categoryId);
                checkCell.setChecked(!enabled);
            } else {
                presentFragment(new NotificationsCustomSettingsActivity(NotificationsController.TYPE_CATEGORY, new ArrayList<>(), true, categoryId), true);
            }
        });

        ActionBarMenu menu = actionBar.createMenu();
        doneButton = menu.addItemWithWidth(done_button, R.drawable.ic_done, AndroidUtilities.dp(56));
        doneButton.setContentDescription(LocaleController.getString("Done", R.string.Done));

        settingsSectionCell = new ShadowSectionCell(context);
        linearLayout1.addView(settingsSectionCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        deleteContainer = new FrameLayout(context);
        deleteContainer.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        linearLayout1.addView(deleteContainer, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        deleteCell = new TextSettingsCell(context);
        deleteCell.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteRedText5));
        deleteCell.setBackgroundDrawable(Theme.getSelectorDrawable(false));
        deleteCell.setText(LocaleController.getString("DeleteCategory", R.string.DeleteCategory), false);
        deleteContainer.addView(deleteCell, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        deleteCell.setOnClickListener(v -> deleteCategoryAlert());

        deleteInfoCell = new ShadowSectionCell(context);
        deleteInfoCell.setBackgroundDrawable(Theme.getThemedDrawable(context, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
        linearLayout1.addView(deleteInfoCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));


        nameTextView.setText(category.getName());
        nameTextView.setSelection(nameTextView.length());
        if (!oldAvatarPath.isEmpty()) {
            avatarImage.setImage(ImageLocation.getForPath(oldAvatarPath), "50_50", avatarDrawable, null);
        } else {
            avatarImage.setImageDrawable(avatarDrawable);
        }

        return fragmentView;
    }

    @Override
    public void didUploadPhoto(final TLRPC.InputFile file, final TLRPC.PhotoSize bigSize, final TLRPC.PhotoSize smallSize) {
        AndroidUtilities.runOnUIThread(() -> {
            if (file != null) {
                uploadedAvatar = file;
                newAvatarPath = imageUpdater.picturePath;
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

    private void processDone() {
        if (donePressed || nameTextView == null) {
            return;
        }
        if (nameTextView.length() == 0) {
            Vibrator v = (Vibrator) getParentActivity().getSystemService(Context.VIBRATOR_SERVICE);
            if (v != null) {
                v.vibrate(200);
            }
            AndroidUtilities.shakeView(nameTextView, 2, 0);
            return;
        } else {
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

        if (!category.getName().equals(nameTextView.getText().toString()) || !oldAvatarPath.equals(newAvatarPath)) {
            updateCategory();
        }
        finishFragment();
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
        imageUpdater.onActivityResult(requestCode, resultCode, data);
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
    }

    private void deleteCategoryAlert(){
        AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(getParentActivity());
        dialogBuilder.setTitle(LocaleController.formatString("DeleteCategory", R.string.DeleteCategoryTitle));
        dialogBuilder.setMessage(LocaleController.getString("AreYouSureDeleteCategory", R.string.AreYouSureDeleteCategory));
        dialogBuilder.setPositiveButton(LocaleController.getString("Delete", R.string.Delete), (dialog1, which1) -> {
            getMessagesController().setDialogsInTransaction(true);
            deleteCategory();
        });
        dialogBuilder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
        AlertDialog alertDialog = dialogBuilder.create();
        showDialog(alertDialog);
        TextView button = (TextView) alertDialog.getButton(DialogInterface.BUTTON_POSITIVE);
        if (button != null) {
            button.setTextColor(Theme.getColor(Theme.key_dialogTextRed2));
        }

    }

    private void deleteCategory() {
        long userId = UserConfig.getInstance(currentAccount).getClientUserId();
        disposables.add(
                storage.deleteCategory(categoryId, userId)
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(() -> {
                                    getMessagesController().reloadDbCategories();
                                    delegate.onDeleteCategory(CategoryEditActivity.this);
                                    Log.d("Local Storage", "Category " + categoryId + " deleted");
                                }, Throwable::printStackTrace
                        )
        );
    }

    private void updateCategory() {
        long userId = UserConfig.getInstance(currentAccount).getClientUserId();

        if (categoryName == null || categoryName.isEmpty()) {
            return;
        }

        if (newAvatarPath == null) {
            newAvatarPath = "";
        }

        disposables.add(
                storage.updateCategory(categoryId, userId, categoryName, newAvatarPath)
                        .subscribeOn(Schedulers.io())
                        .doOnComplete(this::finishFragment)
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(() -> {
                                    getMessagesController().reloadDbCategories();
                                    getNotificationCenter().postNotificationName(NotificationCenter.refreshDialogsWithCategoriesList, MessagesController.UPDATE_MASK_CATEGORY);
                                    Log.d("Local Storage", "Category " + categoryName + " updated");
                                }, Throwable::printStackTrace
                        )
        );
    }

    public void setDelegate(CategoryEditDelegate categoryEditDelegate) {
        delegate = categoryEditDelegate;
    }

    public interface CategoryEditDelegate {
        void onDeleteCategory(CategoryEditActivity fragment);
    }

    @Override
    public ThemeDescription[] getThemeDescriptions() {
        ThemeDescription.ThemeDescriptionDelegate cellDelegate = () -> {
            if (avatarImage != null) {
                avatarDrawable.setInfo(5, null, null);
                avatarImage.invalidate();
            }
        };
        return new ThemeDescription[]{
                new ThemeDescription(fragmentView, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_windowBackgroundGray),

                new ThemeDescription(actionBar, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_actionBarDefault),
                new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_ITEMSCOLOR, null, null, null, null, Theme.key_actionBarDefaultIcon),
                new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_TITLECOLOR, null, null, null, null, Theme.key_actionBarDefaultTitle),
                new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SELECTORCOLOR, null, null, null, null, Theme.key_actionBarDefaultSelector),


                new ThemeDescription(notificationsCell, ThemeDescription.FLAG_SELECTOR, null, null, null, null, Theme.key_listSelector),
                new ThemeDescription(notificationsCell, 0, new Class[]{TextDetailCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText),
                new ThemeDescription(notificationsCell, 0, new Class[]{TextDetailCell.class}, new String[]{"valueTextView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText2),

                new ThemeDescription(nameTextView, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteBlackText),
                new ThemeDescription(nameTextView, ThemeDescription.FLAG_HINTTEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteHintText),
                new ThemeDescription(nameTextView, ThemeDescription.FLAG_BACKGROUNDFILTER, null, null, null, null, Theme.key_windowBackgroundWhiteInputField),
                new ThemeDescription(nameTextView, ThemeDescription.FLAG_BACKGROUNDFILTER | ThemeDescription.FLAG_DRAWABLESELECTEDSTATE, null, null, null, null, Theme.key_windowBackgroundWhiteInputFieldActivated),

                new ThemeDescription(avatarContainer, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_windowBackgroundWhite),
                new ThemeDescription(settingsContainer, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_windowBackgroundWhite),
                new ThemeDescription(typeEditContainer, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_windowBackgroundWhite),
                new ThemeDescription(deleteContainer, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_windowBackgroundWhite),

                new ThemeDescription(settingsTopSectionCell, ThemeDescription.FLAG_BACKGROUNDFILTER, new Class[]{ShadowSectionCell.class}, null, null, null, Theme.key_windowBackgroundGrayShadow),
                new ThemeDescription(settingsSectionCell, ThemeDescription.FLAG_BACKGROUNDFILTER, new Class[]{ShadowSectionCell.class}, null, null, null, Theme.key_windowBackgroundGrayShadow),
                new ThemeDescription(deleteInfoCell, ThemeDescription.FLAG_BACKGROUNDFILTER, new Class[]{ShadowSectionCell.class}, null, null, null, Theme.key_windowBackgroundGrayShadow),

                new ThemeDescription(deleteCell, ThemeDescription.FLAG_SELECTOR, null, null, null, null, Theme.key_listSelector),
                new ThemeDescription(deleteCell, ThemeDescription.FLAG_TEXTCOLOR, new Class[]{TextSettingsCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteRedText5),

                new ThemeDescription(null, 0, null, null, new Drawable[]{Theme.avatar_savedDrawable}, cellDelegate, Theme.key_avatar_text),
                new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundRed),
                new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundOrange),
                new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundViolet),
                new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundGreen),
                new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundCyan),
                new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundBlue),
                new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundPink),
        };
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {

    }
}
