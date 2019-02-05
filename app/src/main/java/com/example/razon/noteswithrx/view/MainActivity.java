package com.example.razon.noteswithrx.view;

import android.content.DialogInterface;
import android.graphics.Color;
import android.os.Build;
import android.support.design.widget.CoordinatorLayout;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.DefaultItemAnimator;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.example.razon.noteswithrx.R;
import com.example.razon.noteswithrx.network.ApiClient;
import com.example.razon.noteswithrx.network.ApiService;
import com.example.razon.noteswithrx.network.model.Note;
import com.example.razon.noteswithrx.network.model.User;
import com.example.razon.noteswithrx.utils.MyDividerItemDecoration;
import com.example.razon.noteswithrx.utils.PrefUtils;
import com.example.razon.noteswithrx.utils.RecyclerTouchListener;
import com.jakewharton.retrofit2.adapter.rxjava2.HttpException;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import butterknife.BindView;
import butterknife.ButterKnife;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.functions.Function;
import io.reactivex.observers.DisposableCompletableObserver;
import io.reactivex.observers.DisposableSingleObserver;
import io.reactivex.schedulers.Schedulers;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = MainActivity.class.getSimpleName();
    private ApiService apiService;
    private NotesAdapter mAdapter;
    private List<Note> notesList = new ArrayList<>();
    private CompositeDisposable compositeDisposable = new CompositeDisposable();


    @BindView(R.id.coordinator_layout)
    CoordinatorLayout coordinatorLayout;

    @BindView(R.id.toolbar)
    Toolbar toolbar;


    @BindView(R.id.recycler_view)
    RecyclerView recyclerView;

    @BindView(R.id.txt_empty_notes_view)
    TextView noNotesView;

    @BindView(R.id.fab)
    FloatingActionButton fab;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);

        setSupportActionBar(toolbar);
        getSupportActionBar().setTitle(getString(R.string.activity_title_home));


        fab.setOnClickListener(view -> {
            showNoteDialog(false, null, -1);
        });

        whiteNotificationBar(fab);

        apiService = ApiClient.getClient(getApplicationContext()).create(ApiService.class);

        mAdapter = new NotesAdapter(this);
        RecyclerView.LayoutManager mLayoutManager = new LinearLayoutManager(getApplicationContext());
        recyclerView.setLayoutManager(mLayoutManager);
        recyclerView.setItemAnimator(new DefaultItemAnimator());
        recyclerView.addItemDecoration(new MyDividerItemDecoration(this, LinearLayoutManager.VERTICAL, 16));
        recyclerView.setAdapter(mAdapter);


        recyclerView.addOnItemTouchListener(new RecyclerTouchListener(this,
                recyclerView, new RecyclerTouchListener.ClickListener() {
            @Override
            public void onClick(View view, final int position) {
            }

            @Override
            public void onLongClick(View view, int position) {
                showActionsDialog(position);
            }
        }));


        if (TextUtils.isEmpty(PrefUtils.getApiKey(this))) {
            registerUser();
        } else {
            // user is already registered, fetch all notes
            fetchAllNotes();
        }


    }

    private void registerUser() {

        String uniqueId = UUID.randomUUID().toString();

        compositeDisposable.add(
                apiService
                        .register(uniqueId)
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribeWith(new DisposableSingleObserver<User>() {

                            @Override
                            public void onSuccess(User user) {
                                // Storing user API Key in preferences
                                PrefUtils.storeApiKey(getApplicationContext(), user.getApiKey());

                                Toast.makeText(getApplicationContext(),
                                        "Device is registered successfully! ApiKey: " + PrefUtils.getApiKey(getApplicationContext()),
                                        Toast.LENGTH_LONG).show();
                            }

                            @Override
                            public void onError(Throwable e) {
                                Log.e(TAG, "onError: " + e.getMessage());
                                showError(e);
                            }
                        })
        );

    }


    private void fetchAllNotes() {

        compositeDisposable.add(

                apiService
                        .fetchAllNotes()
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .map(new Function<List<Note>, List<Note>>() {
                            @Override
                            public List<Note> apply(List<Note> notes) throws Exception {
                                Collections.sort(notes, (n1, n2) -> n1.getId() - n2.getId());
                                return notes;
                            }
                        })
                        .subscribeWith(new DisposableSingleObserver<List<Note>>() {
                            @Override
                            public void onSuccess(List<Note> notes) {


                                notesList.clear();
                                notesList.addAll(notes);
                                mAdapter.addAllData(notesList);
                                toggleEmptyNotes();

                            }

                            @Override
                            public void onError(Throwable e) {
                                Log.e(TAG, "onError: " + e.getMessage());
                                showError(e);
                            }
                        })
        );

    }

    private void createNote(String note) {

        compositeDisposable.add(

                apiService
                .createNote(note)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribeWith(new DisposableSingleObserver<Note>(){

                    @Override
                    public void onSuccess(Note note) {
                        if (!TextUtils.isEmpty(note.getError())) {
                            Toast.makeText(getApplicationContext(), note.getError(), Toast.LENGTH_LONG).show();
                            return;
                        }

                        Log.d(TAG, "new note created: " + note.getId() + ", " + note.getNote() + ", " + note.getTimestamp());

                        notesList.add(0, note);
                       mAdapter.notifyItemInserted(0);

                        toggleEmptyNotes();
                    }

                    @Override
                    public void onError(Throwable e) {
                        Log.e(TAG, "onError: " + e.getMessage());
                        showError(e);
                    }
                })

        );

    }

    private void updateNote(int id, String note, int position) {

        compositeDisposable.add(

                apiService
                .updateNote(id, note)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribeWith(new DisposableCompletableObserver(){

                    @Override
                    public void onComplete() {
                        Log.d(TAG, "Note updated!");

                        Note n = notesList.get(position);
                        n.setNote(note);
                        notesList.set(position, n);
                        mAdapter.notifyItemChanged(position);

                    }

                    @Override
                    public void onError(Throwable e) {
                        Log.e(TAG, "onError: " + e.getMessage());
                        showError(e);
                    }
                })

        );

    }

    private void deleteNote(int id, int position) {

        compositeDisposable.add(

                apiService
                .deleteNote(id)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribeWith(new DisposableCompletableObserver(){

                    @Override
                    public void onComplete() {
                        Log.d(TAG, "Note deleted! " + id);

                        notesList.remove(position);
                       mAdapter.notifyItemRemoved(position);

                        Toast.makeText(MainActivity.this, "Note deleted!", Toast.LENGTH_SHORT).show();

                        toggleEmptyNotes();
                    }

                    @Override
                    public void onError(Throwable e) {
                        Log.e(TAG, "onError: " + e.getMessage());
                        showError(e);
                    }
                })

        );

    }


    private void showActionsDialog(final int position) {
        CharSequence colors[] = new CharSequence[]{"Edit", "Delete"};

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Choose option");
        builder.setItems(colors, (dialog, which)-> {
                if (which == 0) {
                    showNoteDialog(true, notesList.get(position), position);
                } else {
                    deleteNote(notesList.get(position).getId(), position);
                }

        });
        builder.show();
    }





    private void showNoteDialog(final boolean shouldUpdate, final Note note, final int position) {
        LayoutInflater layoutInflaterAndroid = LayoutInflater.from(getApplicationContext());
        View view = layoutInflaterAndroid.inflate(R.layout.note_dialog, null);

        AlertDialog.Builder alertDialogBuilderUserInput = new AlertDialog.Builder(MainActivity.this);
        alertDialogBuilderUserInput.setView(view);

        final EditText inputNote = view.findViewById(R.id.note);
        TextView dialogTitle = view.findViewById(R.id.dialog_title);
        dialogTitle.setText(!shouldUpdate ? getString(R.string.lbl_new_note_title) : getString(R.string.lbl_edit_note_title));

        if (shouldUpdate && note != null) {
            inputNote.setText(note.getNote());
        }
        alertDialogBuilderUserInput
                .setCancelable(false)
                .setPositiveButton(shouldUpdate ? "update" : "save", (dialog, id)->{})
                .setNegativeButton("cancel", (dialogBox, id)->dialogBox.cancel());

        final AlertDialog alertDialog = alertDialogBuilderUserInput.create();
        alertDialog.show();

        alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {

            if (TextUtils.isEmpty(inputNote.getText().toString())) {
                Toast.makeText(MainActivity.this, "Enter note!", Toast.LENGTH_SHORT).show();
                return;
            } else {
                alertDialog.dismiss();
            }

            // check if user updating note
            if (shouldUpdate && note != null) {
                // update note by it's id
                updateNote(note.getId(), inputNote.getText().toString(), position);
            } else {
                // create new note
                createNote(inputNote.getText().toString());
            }
        });
    }



    private void showError(Throwable e) {

        String message = "";
        try {
            if (e instanceof IOException) {
                message = "No internet connection!";
            } else if (e instanceof HttpException) {
                HttpException error = (HttpException) e;
                String errorBody = error.response().errorBody().string();
                JSONObject jObj = new JSONObject(errorBody);

                message = jObj.getString("error");
            }
        } catch (IOException e1) {
            e1.printStackTrace();
        } catch (JSONException e1) {
            e1.printStackTrace();
        } catch (Exception e1) {
            e1.printStackTrace();
        }

        if (TextUtils.isEmpty(message)) {
            message = "Unknown error occurred! Check LogCat.";
        }

        Snackbar snackbar = Snackbar
                .make(coordinatorLayout, message, Snackbar.LENGTH_LONG);

        View sbView = snackbar.getView();
        TextView textView = sbView.findViewById(android.support.design.R.id.snackbar_text);
        textView.setTextColor(Color.YELLOW);
        snackbar.show();

    }

    private void whiteNotificationBar(View view) {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            int flags = view.getSystemUiVisibility();
            flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            view.setSystemUiVisibility(flags);
            getWindow().setStatusBarColor(Color.WHITE);
        }

    }

    private void toggleEmptyNotes() {
        if (notesList.size() > 0) {
            noNotesView.setVisibility(View.GONE);
            recyclerView.setVisibility(View.VISIBLE);
        } else {
            noNotesView.setVisibility(View.VISIBLE);
            recyclerView.setVisibility(View.GONE);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        compositeDisposable.dispose();
    }

}
