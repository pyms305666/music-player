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
import app.musicplayer.android.data.TrackEntry;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

public final class TrackAdapter extends ListAdapter<TrackRow, TrackAdapter.Holder> {
    private final Consumer<TrackEntry> onClick;
    private String selectedKey;
    private static final DiffUtil.ItemCallback<TrackRow> DIFF = new DiffUtil.ItemCallback<>() {
        @Override public boolean areItemsTheSame(@NonNull TrackRow old, @NonNull TrackRow next) {
            return old.key().equals(next.key());
        }
        @Override public boolean areContentsTheSame(@NonNull TrackRow old, @NonNull TrackRow next) {
            return Objects.equals(old.title(), next.title()) && Objects.equals(old.artist(), next.artist())
                    && Objects.equals(old.fileName(), next.fileName());
        }
    };

    public TrackAdapter(Consumer<TrackEntry> onClick) {
        super(DIFF);
        this.onClick = onClick;
    }

    public void submit(List<TrackRow> values, Runnable committed) {
        submitList(values, committed);
    }

    public TrackEntry selected() {
        return getCurrentList().stream().filter(row -> Objects.equals(row.key(), selectedKey))
                .map(TrackRow::entry).findFirst().orElse(null);
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_track, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        TrackRow row = getItem(position);
        holder.title.setText(row.title());
        holder.subtitle.setText(holder.itemView.getContext().getString(
                R.string.track_subtitle, row.artist(), row.fileName()));
        holder.itemView.setBackgroundResource(Objects.equals(row.key(), selectedKey)
                ? R.drawable.track_item_selected_background : R.drawable.track_item_background);
        holder.itemView.setOnClickListener(view -> {
            int next = holder.getBindingAdapterPosition();
            if (next == RecyclerView.NO_POSITION) return;
            String old = selectedKey;
            TrackRow clicked = getItem(next);
            selectedKey = clicked.key();
            for (int index = 0; index < getItemCount(); index++) {
                String key = getItem(index).key();
                if (Objects.equals(key, old) || Objects.equals(key, selectedKey)) notifyItemChanged(index);
            }
            onClick.accept(clicked.entry());
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
