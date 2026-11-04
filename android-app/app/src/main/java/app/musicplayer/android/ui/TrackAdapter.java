package app.musicplayer.android.ui;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import app.musicplayer.android.R;
import app.musicplayer.android.data.TrackEntry;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;

/** Android 本地歌曲 RecyclerView 适配器。基于 TrackRow 的不可变内容比较更新列表，保存选择并分发点击与长按操作。 */
public final class TrackAdapter extends ListAdapter<TrackRow, TrackAdapter.Holder> {
    private final Consumer<TrackEntry> onClick;
    private String selectedKey;
    private static final DiffUtil.ItemCallback<TrackRow> DIFF =
            new DiffUtil.ItemCallback<>() {
                /** 比较列表行的稳定身份，决定是否代表同一歌曲。 */
                @Override
                public boolean areItemsTheSame(@NonNull TrackRow old, @NonNull TrackRow next) {
                    return old.key().equals(next.key());
                }

                /** 比较不可变显示内容，决定列表行是否需要重绘。 */
                @Override
                public boolean areContentsTheSame(@NonNull TrackRow old, @NonNull TrackRow next) {
                    return Objects.equals(old.title(), next.title())
                            && Objects.equals(old.artist(), next.artist())
                            && Objects.equals(old.fileName(), next.fileName());
                }
            };

    /** 初始化本类型的状态和依赖；生命周期与资源归属见类型说明。 */
    public TrackAdapter(Consumer<TrackEntry> onClick) {
        super(DIFF);
        this.onClick = onClick;
    }

    /** 提交后台工作并返回结果句柄，完成或失败由句柄通知调用者。 */
    public void submit(List<TrackRow> values, Runnable committed) {
        submitList(values, committed);
    }

    /** 返回当前选择的歌曲或任务，没有选择时返回空值。 */
    public TrackEntry selected() {
        return getCurrentList().stream()
                .filter(row -> Objects.equals(row.key(), selectedKey))
                .map(TrackRow::entry)
                .findFirst()
                .orElse(null);
    }

    /** 创建并缓存歌曲行控件，供 RecyclerView 重复使用。 */
    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(
                LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.item_track, parent, false));
    }

    /** 将当前歌曲快照绑定到复用行，并更新点击目标。 */
    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        TrackRow row = getItem(position);
        holder.title.setText(row.title());
        Context context = holder.itemView.getContext();
        holder.subtitle.setText(
                context.getString(R.string.track_subtitle, row.artist(), row.fileName()));

        String fileName = row.fileName();
        String lower = fileName != null ? fileName.toLowerCase(Locale.ROOT) : "";
        if (lower.endsWith(".flac") || lower.endsWith(".wav") || lower.endsWith(".ape") || lower.endsWith(".aiff")) {
            holder.itemBadge.setText("FLAC");
            holder.itemBadge.setBackgroundResource(R.drawable.badge_flac_background);
            holder.itemBadge.setTextColor(ContextCompat.getColor(context, R.color.badge_flac_text));
            holder.itemBadge.setVisibility(View.VISIBLE);
        } else if (lower.endsWith(".mp3") || lower.endsWith(".m4a") || lower.endsWith(".aac") || lower.endsWith(".ogg")) {
            holder.itemBadge.setText("MP3");
            holder.itemBadge.setBackgroundResource(R.drawable.badge_std_background);
            holder.itemBadge.setTextColor(ContextCompat.getColor(context, R.color.badge_std_text));
            holder.itemBadge.setVisibility(View.VISIBLE);
        } else {
            holder.itemBadge.setVisibility(View.GONE);
        }

        holder.itemView.setBackgroundResource(
                Objects.equals(row.key(), selectedKey)
                        ? R.drawable.track_item_selected_background
                        : R.drawable.track_item_background);
        holder.itemView.setOnClickListener(
                view -> {
                    int next = holder.getBindingAdapterPosition();
                    if (next == RecyclerView.NO_POSITION) return;
                    String old = selectedKey;
                    TrackRow clicked = getItem(next);
                    selectedKey = clicked.key();
                    for (int index = 0; index < getItemCount(); index++) {
                        String key = getItem(index).key();
                        if (Objects.equals(key, old) || Objects.equals(key, selectedKey))
                            notifyItemChanged(index);
                    }
                    onClick.accept(clicked.entry());
                });
    }

    /** 缓存单行控件与点击目标。 */
    static final class Holder extends RecyclerView.ViewHolder {
        final TextView title;
        final TextView subtitle;
        final TextView itemBadge;

        /** 初始化本类型的状态和依赖；生命周期与资源归属见类型说明。 */
        Holder(View view) {
            super(view);
            title = view.findViewById(R.id.itemTitle);
            subtitle = view.findViewById(R.id.itemSubtitle);
            itemBadge = view.findViewById(R.id.itemBadge);
        }
    }
}
