package com.hansanie.greencart.viewmodel;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.hansanie.greencart.database.AppDatabase;
import com.hansanie.greencart.model.UserOffer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class UserOfferViewModel extends AndroidViewModel {
    private final AppDatabase db;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final MutableLiveData<Boolean> insertResult = new MutableLiveData<>();

    public UserOfferViewModel(@NonNull Application application) {
        super(application);
        db = AppDatabase.getInstance(application);
    }

    public void insert(UserOffer offer) {
        executor.execute(() -> {
            db.userOfferDao().insert(offer);
            insertResult.postValue(true);
        });
    }

    public LiveData<Boolean> getInsertResult() {
        return insertResult;
    }
}

