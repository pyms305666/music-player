package app.musicplayer.android.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import app.musicplayer.android.R;
import app.musicplayer.model.OnlineTrackInfo;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/** Android 在线歌曲 RecyclerView 适配器。用稳定身份及 DiffUtil 更新增量结果，并保留用户选择，避免复用行误触其他歌曲。 */
public final class OnlineTrackAdapter
        extends ListAdapter<OnlineTrackInfo, OnlineTrackAdapter.Holder> {
    private final Consumer<OnlineTrackInfo> onClick;
    private String selectedKey;
    private static final DiffUtil.ItemCallback<OnlineTrackInfo> DIFF =
            new DiffUtil.ItemCallback<>() {
                /** 比较列表行的稳定身份，决定是否代表同一歌曲。 */
                @Override
                public boolean areItemsTheSame(
                        @NonNull OnlineTrackInfo old, @NonNull OnlineTrackInfo next) {
                    return old.identity().equals(next.identity());
                }

                /** 比较不可变显示内容，决定列表行是否需要重绘。 */
                @Override
                public boolean areContentsTheSame(
                        @NonNull OnlineTrackInfo old, @NonNull OnlineTrackInfo next) {
                    return old.equals(next);
                }
            };

    /** 初始化本类型的状态和依赖；生命周期与资源归属见类型说明。 */
    public OnlineTrackAdapter(Consumer<OnlineTrackInfo> onClick) {
        super(DIFF);
        this.onClick = onClick;
    }

    /** 提交后台工作并返回结果句柄，完成或失败由句柄通知调用者。 */
    public void submit(List<OnlineTrackInfo> values) {
        submit(values, () -> {});
    }

    /** 提交后台工作并返回结果句柄，完成或失败由句柄通知调用者。 */
    public void submit(List<OnlineTrackInfo> values, Runnable committed) {
        submitList(List.copyOf(values), committed);
    }

    /** 清除旧选择并通知相关行更新。 */
    public void clearSelection() {
        String old = selectedKey;
        selectedKey = null;
        changed(old);
    }

    /** 返回当前选择的歌曲或任务，没有选择时返回空值。 */
    public OnlineTrackInfo selected() {
        return getCurrentList().stream()
                .filter(track -> Objects.equals(selectedKey, track.identity()))
                .findFirst()
                .orElse(null);
    }

    /** 通知观察者当前数据或任务状态已经改变。 */
    private void changed(String key) {
        for (int index = 0; index < getItemCount(); index++)
            if (Objects.equals(getItem(index).identity(), key)) notifyItemChanged(index);
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
        OnlineTrackInfo item = getItem(position);
        holder.title.setText(item.title());
        holder.subtitle.setText(item.subtitle());
        holder.itemView.setBackgroundResource(
                Objects.equals(item.identity(), selectedKey)
                        ? R.drawable.track_item_selected_background
                        : R.drawable.track_item_background);
        holder.itemView.setOnClickListener(
                view -> {
                    int index = holder.getBindingAdapterPosition();
                    if (index == RecyclerView.NO_POSITION) return;
                    String old = selectedKey;
                    OnlineTrackInfo clicked = getItem(index);
                    selectedKey = clicked.identity();
                    changed(old);
                    changed(selectedKey);
                    onClick.accept(clicked);
                });
    }

    /** 缓存单行控件与点击目标。 */
    static final class Holder extends RecyclerView.ViewHolder {
        final TextView title;
        final TextView subtitle;

        /** 初始化本类型的状态和依赖；生命周期与资源归属见类型说明。 */
        Holder(View view) {
            super(view);
            title = view.findViewById(R.id.itemTitle);
            subtitle = view.findViewById(R.id.itemSubtitle);
        }
    }
}
