package com.gathin.tvhome;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Outline;
import android.graphics.PixelFormat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

/**
 * Full-screen library grid adapter displaying all installed apps on the same clean background.
 * Focus state only slightly scales the tile with a smooth ease. No highlight box, ring, or glass panel.
 */
class LibraryGridAdapter extends RecyclerView.Adapter<LibraryGridAdapter.Holder> {

    private final Context ctx;
    private final List<AppAdapter.Entry> items = new ArrayList<>();

    LibraryGridAdapter(Context ctx) {
        this.ctx = ctx;
    }

    void setItems(List<AppAdapter.Entry> newItems) {
        items.clear();
        if (newItems != null) items.addAll(newItems);
        notifyDataSetChanged();
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
        int columns = 6;
        int cell = inner / columns;
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
        nameLp.topMargin = h + Math.round(6 * density);
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

        h.card.setOnClickListener(v -> launch(e));
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
