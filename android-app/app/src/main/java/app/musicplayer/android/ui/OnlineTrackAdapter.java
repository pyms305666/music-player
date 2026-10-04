package app.musicplayer.android.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.DiffUtil;

import app.musicplayer.android.R;
import app.musicplayer.model.OnlineTrackInfo;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

public final class OnlineTrackAdapter extends ListAdapter<OnlineTrackInfo, OnlineTrackAdapter.Holder> {
    private final Consumer<OnlineTrackInfo> onClick;
    private String selectedKey;
    private static final DiffUtil.ItemCallback<OnlineTrackInfo> DIFF = new DiffUtil.ItemCallback<>() {
        @Override public boolean areItemsTheSame(@NonNull OnlineTrackInfo old, @NonNull OnlineTrackInfo next) {
            return old.identity().equals(next.identity());
        }
        @Override public boolean areContentsTheSame(@NonNull OnlineTrackInfo old, @NonNull OnlineTrackInfo next) {
            return old.equals(next);
        }
    };

    public OnlineTrackAdapter(Consumer<OnlineTrackInfo> onClick) {
        super(DIFF);
        this.onClick = onClick;
    }

    public void submit(List<OnlineTrackInfo> values) {
        submit(values, () -> { });
    }
    public void submit(List<OnlineTrackInfo> values, Runnable committed) { submitList(List.copyOf(values), committed); }
    public void clearSelection() { String old = selectedKey; selectedKey = null; changed(old); }

    public OnlineTrackInfo selected() {
        return getCurrentList().stream().filter(track -> Objects.equals(selectedKey, track.identity())).findFirst().orElse(null);
    }
    private void changed(String key) {
        for (int index = 0; index < getItemCount(); index++) if (Objects.equals(getItem(index).identity(), key)) notifyItemChanged(index);
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_track, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        OnlineTrackInfo item = getItem(position);
        holder.title.setText(item.title());
        holder.subtitle.setText(item.subtitle());
        holder.itemView.setBackgroundResource(Objects.equals(item.identity(), selectedKey)
                ? R.drawable.track_item_selected_background : R.drawable.track_item_background);
        holder.itemView.setOnClickListener(view -> {
            int index = holder.getBindingAdapterPosition();
            if (index == RecyclerView.NO_POSITION) return;
            String old = selectedKey;
            OnlineTrackInfo clicked = getItem(index);
            selectedKey = clicked.identity(); changed(old); changed(selectedKey);
            onClick.accept(clicked);
        });
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final TextView title;
        final TextView subtitle;

        Holder(View view) {
            super(view);
            title = view.findViewById(R.id.itemTitle);
            subtitle = view.findViewById(R.id.itemSubtitle);
        }
    }
}
