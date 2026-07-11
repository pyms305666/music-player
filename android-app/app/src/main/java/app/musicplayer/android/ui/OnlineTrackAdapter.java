package app.musicplayer.android.ui;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import app.musicplayer.android.R;
import app.musicplayer.model.OnlineTrackInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public final class OnlineTrackAdapter extends RecyclerView.Adapter<OnlineTrackAdapter.Holder> {
    private final List<OnlineTrackInfo> items = new ArrayList<>();
    private final Consumer<OnlineTrackInfo> onClick;
    private int selectedPosition = RecyclerView.NO_POSITION;

    public OnlineTrackAdapter(Consumer<OnlineTrackInfo> onClick) {
        this.onClick = onClick;
    }

    public void submit(List<OnlineTrackInfo> values) {
        items.clear();
        items.addAll(values);
        selectedPosition = RecyclerView.NO_POSITION;
        notifyDataSetChanged();
    }

    public OnlineTrackInfo selected() {
        return selectedPosition >= 0 && selectedPosition < items.size() ? items.get(selectedPosition) : null;
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_track, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        OnlineTrackInfo item = items.get(position);
        holder.title.setText(item.title());
        holder.subtitle.setText(item.subtitle());
        holder.itemView.setBackgroundColor(position == selectedPosition ? Color.rgb(55, 61, 68) : Color.TRANSPARENT);
        holder.itemView.setOnClickListener(view -> {
            int old = selectedPosition;
            selectedPosition = holder.getBindingAdapterPosition();
            if (old != RecyclerView.NO_POSITION) notifyItemChanged(old);
            notifyItemChanged(selectedPosition);
            onClick.accept(item);
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
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
