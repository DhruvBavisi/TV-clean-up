package com.gathin.tvhome;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.PixelFormat;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.provider.Settings;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

/**
 * Full-screen library grid adapter displaying all installed apps.
 * Supports hold-OK / menu key options: Move (with animated D-pad reordering) and Open in Settings.
 * Custom app order is saved persistently in SharedPreferences.
 */
class LibraryGridAdapter extends RecyclerView.Adapter<LibraryGridAdapter.Holder> {

    private static final String PREF_NAME = "library_order_prefs";
    private static final String KEY_CUSTOM_ORDER = "custom_app_order";
    private static final int COLUMNS = 6;

    private final Context ctx;
    private final List<AppAdapter.Entry> items = new ArrayList<>();
    private int movingPosition = -1;
    private RecyclerView attachedRecyclerView;

    LibraryGridAdapter(Context ctx) {
        this.ctx = ctx;
    }

    @Override
    public void onAttachedToRecyclerView(@NonNull RecyclerView recyclerView) {
        super.onAttachedToRecyclerView(recyclerView);
        this.attachedRecyclerView = recyclerView;
    }

    void setItems(List<AppAdapter.Entry> newItems) {
        items.clear();
        if (newItems != null) {
            SharedPreferences prefs = ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
            String savedOrder = prefs.getString(KEY_CUSTOM_ORDER, null);
            if (savedOrder != null && !savedOrder.isEmpty()) {
                String[] orderKeys = savedOrder.split(";");
                List<AppAdapter.Entry> pool = new ArrayList<>(newItems);
                for (String key : orderKeys) {
                    for (int i = 0; i < pool.size(); i++) {
                        AppAdapter.Entry e = pool.get(i);
                        if (e.component != null && key.equals(e.component.flattenToShortString())) {
                            items.add(e);
                            pool.remove(i);
                            break;
                        }
                    }
                }
                items.addAll(pool); // Any newly installed apps appended at the end
            } else {
                items.addAll(newItems);
            }
        }
        notifyDataSetChanged();
    }

    private void saveCustomOrder() {
        StringBuilder sb = new StringBuilder();
        for (AppAdapter.Entry e : items) {
            if (e.component != null) {
                sb.append(e.component.flattenToShortString()).append(";");
            }
        }
        ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_CUSTOM_ORDER, sb.toString())
                .apply();
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_app, parent, false);
        float density = parent.getResources().getDisplayMetrics().density;
        int width = parent.getWidth() > 0 ? parent.getWidth() : parent.getMeasuredWidth();
        if (width <= 0) width = Math.round(1920 * density / 2f);
        int inner = width - parent.getPaddingLeft() - parent.getPaddingRight();
        int cell = inner / COLUMNS;
        int w = Math.max(Math.round(80 * density), (int) ((cell - 14 * density) / 1.08f));
        int h = Math.round(w * 101f / 180f);

        View card = v.findViewById(R.id.card);
        ViewGroup.LayoutParams cardLp = card.getLayoutParams();
        cardLp.width = w;
        cardLp.height = h;
        card.setLayoutParams(cardLp);

        View name = v.findViewById(R.id.name);
        ViewGroup.MarginLayoutParams nameLp = (ViewGroup.MarginLayoutParams) name.getLayoutParams();
        nameLp.width = w;
        nameLp.topMargin = Math.round(6 * density);
        name.setLayoutParams(nameLp);

        return new Holder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int position) {
        AppAdapter.Entry e = items.get(position);
        h.name.setText(e.label);
        h.name.setAlpha(0.75f);

        if (e.banner != null) {
            h.banner.setImageDrawable(e.banner);
            h.banner.setVisibility(View.VISIBLE);
            h.icon.setVisibility(View.GONE);
            h.label.setVisibility(View.GONE);
            h.card.setBackground(e.banner.getOpacity() == PixelFormat.OPAQUE ? null : h.cardBackground);
        } else {
            h.banner.setImageDrawable(null);
            h.banner.setVisibility(View.GONE);
            h.icon.setImageDrawable(e.icon);
            h.icon.setVisibility(View.VISIBLE);
            h.label.setText(e.label);
            h.label.setVisibility(View.VISIBLE);
            h.card.setBackground(h.cardBackground);
        }

        final float density = h.card.getResources().getDisplayMetrics().density;
        if (movingPosition == position) {
            h.card.setScaleX(1.15f);
            h.card.setScaleY(1.15f);
            h.card.setTranslationZ(18f * density);
            h.name.setAlpha(1.0f);
        } else {
            h.card.setTranslationZ(0f);
            if (!h.card.isFocused()) {
                h.card.setScaleX(1.0f);
                h.card.setScaleY(1.0f);
            }
        }

        h.card.setOnClickListener(v -> {
            if (movingPosition != -1) {
                stopMoveMode(h.getBindingAdapterPosition());
            } else {
                launch(e);
            }
        });

        h.card.setOnLongClickListener(v -> {
            if (movingPosition != -1) return true;
            int pos = h.getBindingAdapterPosition();
            if (pos != RecyclerView.NO_POSITION) {
                showOptionsMenu(pos, v);
                return true;
            }
            return false;
        });

        h.card.setOnKeyListener((view, keyCode, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
            int pos = h.getBindingAdapterPosition();
            if (pos == RecyclerView.NO_POSITION) return false;

            if (movingPosition == pos) {
                int target = -1;
                if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT && pos > 0) {
                    target = pos - 1;
                } else if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT && pos < items.size() - 1) {
                    target = pos + 1;
                } else if (keyCode == KeyEvent.KEYCODE_DPAD_UP && pos >= COLUMNS) {
                    target = pos - COLUMNS;
                } else if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN && pos + COLUMNS < items.size()) {
                    target = pos + COLUMNS;
                } else if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER
                        || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER || keyCode == KeyEvent.KEYCODE_BACK) {
                    stopMoveMode(pos);
                    return true;
                }

                if (target != -1) {
                    AppAdapter.Entry item = items.remove(pos);
                    items.add(target, item);
                    movingPosition = target;
                    notifyItemMoved(pos, target);
                    if (attachedRecyclerView != null) {
                        final int focusTarget = target;
                        attachedRecyclerView.post(() -> {
                            RecyclerView.ViewHolder vh = attachedRecyclerView.findViewHolderForAdapterPosition(focusTarget);
                            if (vh != null && vh.itemView != null) {
                                View targetCard = vh.itemView.findViewById(R.id.card);
                                if (targetCard != null) targetCard.requestFocus();
                            }
                        });
                    }
                    return true;
                }
                return true;
            } else {
                if (keyCode == KeyEvent.KEYCODE_MENU) {
                    showOptionsMenu(pos, view);
                    return true;
                }
            }
            return false;
        });
    }

    private void showOptionsMenu(int position, View anchor) {
        if (position < 0 || position >= items.size()) return;
        AppAdapter.Entry entry = items.get(position);

        LayoutInflater inflater = LayoutInflater.from(ctx);
        View panel = inflater.inflate(R.layout.popup_inputs, null);
        LinearLayout rows = panel.findViewById(R.id.rows);

        float density = ctx.getResources().getDisplayMetrics().density;
        PopupWindow popup = new PopupWindow(panel, (int) (220 * density), ViewGroup.LayoutParams.WRAP_CONTENT, true);
        popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popup.setElevation(16 * density);
        popup.setOutsideTouchable(true);

        // Option 1: Move
        View rowMove = inflater.inflate(R.layout.item_input, rows, false);
        ((ImageView) rowMove.findViewById(R.id.icon)).setImageResource(R.drawable.ic_swap_vert);
        ((TextView) rowMove.findViewById(R.id.title)).setText(R.string.tile_move);
        rowMove.findViewById(R.id.status).setVisibility(View.GONE);
        rowMove.findViewById(R.id.dot).setVisibility(View.GONE);
        rowMove.setOnClickListener(v -> {
            popup.dismiss();
            startMoveMode(position);
        });
        rows.addView(rowMove);

        // Option 2: Open in settings
        View rowSettings = inflater.inflate(R.layout.item_input, rows, false);
        ((ImageView) rowSettings.findViewById(R.id.icon)).setImageResource(R.drawable.ic_settings);
        ((TextView) rowSettings.findViewById(R.id.title)).setText("Open in Settings");
        rowSettings.findViewById(R.id.status).setVisibility(View.GONE);
        rowSettings.findViewById(R.id.dot).setVisibility(View.GONE);
        rowSettings.setOnClickListener(v -> {
            popup.dismiss();
            openSettings(entry);
        });
        rows.addView(rowSettings);

        panel.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        popup.showAsDropDown(anchor, 0, (int) (6 * density));
        rowMove.requestFocus();
    }

    private void startMoveMode(int position) {
        movingPosition = position;
        notifyItemChanged(position);
        Toast.makeText(ctx, "Move with arrows, press OK to place", Toast.LENGTH_SHORT).show();
    }

    private void stopMoveMode(int finalPos) {
        movingPosition = -1;
        saveCustomOrder();
        notifyDataSetChanged();
        Toast.makeText(ctx, "Position saved", Toast.LENGTH_SHORT).show();
    }

    private void openSettings(AppAdapter.Entry e) {
        if (e == null || e.component == null) return;
        Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        i.setData(Uri.parse("package:" + e.component.getPackageName()));
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            ctx.startActivity(i);
        } catch (Exception ex) {
            Toast.makeText(ctx, "Cannot open settings", Toast.LENGTH_SHORT).show();
        }
    }

    private void launch(AppAdapter.Entry e) {
        if (e.kind == AppAdapter.Entry.HDMI_INPUT) {
            if (ctx instanceof MainActivity) {
                ((MainActivity) ctx).openInput("com.sony.dtv.tvinput.external/.ExternalTvInputService/HW2");
            }
            return;
        }
        Intent i = new Intent(Intent.ACTION_MAIN)
                .setComponent(e.component)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        try {
            ctx.startActivity(i);
        } catch (Exception ex) {
            Toast.makeText(ctx, ctx.getString(R.string.cant_open, e.label), Toast.LENGTH_SHORT).show();
        }
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final View card;
        final android.graphics.drawable.Drawable cardBackground;
        final ImageView banner;
        final ImageView icon;
        final TextView label;
        final TextView name;

        Holder(View v) {
            super(v);
            card = v.findViewById(R.id.card);
            cardBackground = card.getBackground();
            banner = v.findViewById(R.id.banner);
            icon = v.findViewById(R.id.icon);
            label = v.findViewById(R.id.label);
            name = v.findViewById(R.id.name);

            final float density = v.getResources().getDisplayMetrics().density;
            card.setOutlineProvider(new ViewOutlineProvider() {
                @Override
                public void getOutline(View view, Outline outline) {
                    outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), 16f * density);
                }
            });
            card.setClipToOutline(true);

            card.setOnFocusChangeListener((view, focused) -> {
                view.animate().cancel();
                name.animate().cancel();
                float scale = focused ? 1.08f : 1.0f;
                view.animate().scaleX(scale).scaleY(scale)
                        .setDuration(160)
                        .setInterpolator(new DecelerateInterpolator(1.5f))
                        .start();
                name.animate().alpha(focused ? 1.0f : 0.75f)
                        .setDuration(160)
                        .start();
            });
        }
    }
}
