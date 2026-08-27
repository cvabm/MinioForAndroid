package com.xiaoming.minio;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class ObjectAdapter extends RecyclerView.Adapter<ObjectAdapter.Holder> {
    interface Listener {
        void onClick(ObjectRow row);

        void onLongClick(View anchor, ObjectRow row);
    }

    private final List<ObjectRow> items = new ArrayList<>();
    private final Listener listener;

    ObjectAdapter(Listener listener) {
        this.listener = listener;
    }

    void submit(List<ObjectRow> rows) {
        items.clear();
        if (rows != null) {
            items.addAll(rows);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_object, parent, false);
        return new Holder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        ObjectRow row = items.get(position);
        holder.icon.setText(row.directory ? "📁" : "📄");
        holder.name.setText(row.name);
        holder.meta.setText(row.directory
                ? "目录"
                : formatSize(row.size) + (row.modified == null ? "" : "  ·  " + row.modified));
        holder.itemView.setOnClickListener(v -> listener.onClick(row));
        holder.itemView.setOnLongClickListener(v -> {
            listener.onLongClick(v, row);
            return true;
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static String formatSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        double kb = bytes / 1024.0;
        if (kb < 1024) {
            return String.format(Locale.US, "%.1f KB", kb);
        }
        return String.format(Locale.US, "%.1f MB", kb / 1024.0);
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final TextView icon;
        final TextView name;
        final TextView meta;

        Holder(@NonNull View itemView) {
            super(itemView);
            icon = itemView.findViewById(R.id.icon);
            name = itemView.findViewById(R.id.name);
            meta = itemView.findViewById(R.id.meta);
        }
    }
}
