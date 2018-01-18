package com.example.pfff.service;

import com.example.pfff.model.ReleaseNote;
import com.example.pfff.model.Update;
import com.example.pfff.repository.ReleaseNoteRepository;
import com.example.pfff.repository.UpdateRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import javax.transaction.Transactional;
import java.util.List;

/**
 * Copyright (c) SRG SSR. All rights reserved.
 * <p>
 * License information is available from the LICENSE file.
 */
@Repository
public class UpdateService {
    @Autowired
    private UpdateRepository repository;

    @Transactional
    public Update save(Update update) {
        repository.removeByPackageNameAndVersion(update.packageName, update.version);
        return repository.save(update);
    }

    public Update getUpdate(String packageName, String version) {
        List<Update> updates = repository.findByPackageNameAndVersion(packageName, version);

        if (updates.isEmpty()) {
            return null;
        } else {
            return updates.get(0);
        }
    }
}
