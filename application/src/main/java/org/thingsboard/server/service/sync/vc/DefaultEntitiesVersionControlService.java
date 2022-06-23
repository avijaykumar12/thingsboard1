/**
 * Copyright © 2016-2022 The Thingsboard Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.thingsboard.server.service.sync.vc;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.request.async.DeferredResult;
import org.thingsboard.common.util.DonAsynchron;
import org.thingsboard.common.util.JacksonUtil;
import org.thingsboard.common.util.TbStopWatch;
import org.thingsboard.common.util.ThingsBoardExecutors;
import org.thingsboard.server.cache.CaffeineTbTransactionalCache;
import org.thingsboard.server.cache.TbTransactionalCache;
import org.thingsboard.server.common.data.EntityType;
import org.thingsboard.server.common.data.ExportableEntity;
import org.thingsboard.server.common.data.StringUtils;
import org.thingsboard.server.common.data.audit.ActionType;
import org.thingsboard.server.common.data.exception.ThingsboardErrorCode;
import org.thingsboard.server.common.data.exception.ThingsboardException;
import org.thingsboard.server.common.data.id.EntityId;
import org.thingsboard.server.common.data.id.EntityIdFactory;
import org.thingsboard.server.common.data.id.HasId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.page.PageData;
import org.thingsboard.server.common.data.page.PageLink;
import org.thingsboard.server.common.data.sync.ThrowingRunnable;
import org.thingsboard.server.common.data.sync.ie.EntityExportData;
import org.thingsboard.server.common.data.sync.ie.EntityExportSettings;
import org.thingsboard.server.common.data.sync.ie.EntityImportResult;
import org.thingsboard.server.common.data.sync.ie.EntityImportSettings;
import org.thingsboard.server.common.data.sync.vc.EntityDataDiff;
import org.thingsboard.server.common.data.sync.vc.EntityDataInfo;
import org.thingsboard.server.common.data.sync.vc.EntityLoadError;
import org.thingsboard.server.common.data.sync.vc.EntityTypeLoadResult;
import org.thingsboard.server.common.data.sync.vc.EntityVersion;
import org.thingsboard.server.common.data.sync.vc.RepositorySettings;
import org.thingsboard.server.common.data.sync.vc.VersionCreationResult;
import org.thingsboard.server.common.data.sync.vc.VersionLoadResult;
import org.thingsboard.server.common.data.sync.vc.VersionedEntityInfo;
import org.thingsboard.server.common.data.sync.vc.request.create.AutoVersionCreateConfig;
import org.thingsboard.server.common.data.sync.vc.request.create.ComplexVersionCreateRequest;
import org.thingsboard.server.common.data.sync.vc.request.create.EntityTypeVersionCreateConfig;
import org.thingsboard.server.common.data.sync.vc.request.create.SingleEntityVersionCreateRequest;
import org.thingsboard.server.common.data.sync.vc.request.create.SyncStrategy;
import org.thingsboard.server.common.data.sync.vc.request.create.VersionCreateRequest;
import org.thingsboard.server.common.data.sync.vc.request.load.EntityTypeVersionLoadRequest;
import org.thingsboard.server.common.data.sync.vc.request.load.SingleEntityVersionLoadRequest;
import org.thingsboard.server.common.data.sync.vc.request.load.VersionLoadConfig;
import org.thingsboard.server.common.data.sync.vc.request.load.VersionLoadRequest;
import org.thingsboard.server.dao.DaoUtil;
import org.thingsboard.server.dao.exception.DeviceCredentialsValidationException;
import org.thingsboard.server.queue.util.TbCoreComponent;
import org.thingsboard.server.service.entitiy.TbNotificationEntityService;
import org.thingsboard.server.service.security.model.SecurityUser;
import org.thingsboard.server.service.sync.ie.EntitiesExportImportService;
import org.thingsboard.server.service.sync.ie.exporting.ExportableEntitiesService;
import org.thingsboard.server.service.sync.ie.importing.impl.MissingEntityException;
import org.thingsboard.server.service.sync.vc.autocommit.TbAutoCommitSettingsService;
import org.thingsboard.server.service.sync.vc.data.CommitGitRequest;
import org.thingsboard.server.service.sync.vc.data.ComplexEntitiesExportCtx;
import org.thingsboard.server.service.sync.vc.data.EntitiesExportCtx;
import org.thingsboard.server.service.sync.vc.data.EntitiesImportCtx;
import org.thingsboard.server.service.sync.vc.data.EntityTypeExportCtx;
import org.thingsboard.server.service.sync.vc.data.ReimportTask;
import org.thingsboard.server.service.sync.vc.data.SimpleEntitiesExportCtx;
import org.thingsboard.server.service.sync.vc.repository.TbRepositorySettingsService;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.google.common.util.concurrent.Futures.transform;
import static com.google.common.util.concurrent.Futures.transformAsync;

@Service
@TbCoreComponent
@RequiredArgsConstructor
@Slf4j
public class DefaultEntitiesVersionControlService implements EntitiesVersionControlService {

    private final TbRepositorySettingsService repositorySettingsService;
    private final TbAutoCommitSettingsService autoCommitSettingsService;
    private final GitVersionControlQueueService gitServiceQueue;
    private final EntitiesExportImportService exportImportService;
    private final ExportableEntitiesService exportableEntitiesService;
    private final TbNotificationEntityService entityNotificationService;
    private final TransactionTemplate transactionTemplate;
    private final TbTransactionalCache<UUID, VersionControlTaskCacheEntry> taskCache;

    private ListeningExecutorService executor;

    @Value("${vc.thread_pool_size:4}")
    private int threadPoolSize;

    @PostConstruct
    public void init() {
        executor = MoreExecutors.listeningDecorator(ThingsBoardExecutors.newWorkStealingPool(threadPoolSize, DefaultEntitiesVersionControlService.class));
    }

    @PreDestroy
    public void shutdown() {
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    @SuppressWarnings("UnstableApiUsage")
    @Override
    public ListenableFuture<UUID> saveEntitiesVersion(SecurityUser user, VersionCreateRequest request) throws Exception {
        var pendingCommit = gitServiceQueue.prepareCommit(user, request);
        DonAsynchron.withCallback(pendingCommit, commit -> {
            cachePut(commit.getTxId(), new VersionCreationResult());
            try {
                List<ListenableFuture<Void>> gitFutures = new ArrayList<>();
                switch (request.getType()) {
                    case SINGLE_ENTITY: {
                        handleSingleEntityRequest(new SimpleEntitiesExportCtx(user, commit, (SingleEntityVersionCreateRequest) request));
                        break;
                    }
                    case COMPLEX: {
                        handleComplexRequest(new ComplexEntitiesExportCtx(user, commit, (ComplexVersionCreateRequest) request));
                        break;
                    }
                }
                var resultFuture = Futures.transformAsync(Futures.allAsList(gitFutures), f -> gitServiceQueue.push(commit), executor);
                DonAsynchron.withCallback(resultFuture, result -> cachePut(commit.getTxId(), result), e -> processCommitError(user, request, commit, e), executor);
            } catch (Exception e) {
                processCommitError(user, request, commit, e);
            }
        }, t -> log.debug("[{}] Failed to prepare the commit: {}", user.getId(), request, t));

        return transform(pendingCommit, CommitGitRequest::getTxId, MoreExecutors.directExecutor());
    }

    @Override
    public VersionCreationResult getVersionCreateStatus(SecurityUser user, UUID requestId) throws ThingsboardException {
        return getStatus(user, requestId, VersionControlTaskCacheEntry::getExportResult);
    }

    @Override
    public VersionLoadResult getVersionLoadStatus(SecurityUser user, UUID requestId) throws ThingsboardException {
        return getStatus(user, requestId, VersionControlTaskCacheEntry::getImportResult);
    }

    private <T> T getStatus(SecurityUser user, UUID requestId, Function<VersionControlTaskCacheEntry, T> getter) throws ThingsboardException {
        var cacheEntry = taskCache.get(requestId);
        if (cacheEntry == null || cacheEntry.get() == null) {
            throw new ThingsboardException(ThingsboardErrorCode.ITEM_NOT_FOUND);
        } else {
            var entry = cacheEntry.get();
            var result = getter.apply(entry);
            if (result == null) {
                throw new ThingsboardException(ThingsboardErrorCode.BAD_REQUEST_PARAMS);
            } else {
                return result;
            }
        }
    }

    private void handleSingleEntityRequest(SimpleEntitiesExportCtx ctx) throws Exception {
        ctx.add(saveEntityData(ctx, ctx.getRequest().getEntityId()));
    }

    private void handleComplexRequest(ComplexEntitiesExportCtx parentCtx) {
        ComplexVersionCreateRequest request = parentCtx.getRequest();
        request.getEntityTypes().forEach((entityType, config) -> {
            EntityTypeExportCtx ctx = new EntityTypeExportCtx(parentCtx, config, request.getSyncStrategy(), entityType);
            if (ctx.isOverwrite()) {
                ctx.add(gitServiceQueue.deleteAll(ctx.getCommit(), entityType));
            }

            if (config.isAllEntities()) {
                DaoUtil.processInBatches(pageLink -> exportableEntitiesService.findEntitiesByTenantId(ctx.getTenantId(), entityType, pageLink)
                        , 100, entity -> {
                            try {
                                ctx.add(saveEntityData(ctx, entity.getId()));
                            } catch (Exception e) {
                                throw new RuntimeException(e);
                            }
                        });
            } else {
                for (UUID entityId : config.getEntityIds()) {
                    try {
                        ctx.add(saveEntityData(ctx, EntityIdFactory.getByTypeAndUuid(entityType, entityId)));
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        });
    }

    private ListenableFuture<Void> saveEntityData(EntitiesExportCtx<?> ctx, EntityId entityId) throws Exception {
        EntityExportData<ExportableEntity<EntityId>> entityData = exportImportService.exportEntity(ctx, entityId);
        return gitServiceQueue.addToCommit(ctx.getCommit(), entityData);
    }

    @Override
    public ListenableFuture<PageData<EntityVersion>> listEntityVersions(TenantId tenantId, String branch, EntityId externalId, PageLink pageLink) throws Exception {
        return gitServiceQueue.listVersions(tenantId, branch, externalId, pageLink);
    }

    @Override
    public ListenableFuture<PageData<EntityVersion>> listEntityTypeVersions(TenantId tenantId, String branch, EntityType entityType, PageLink pageLink) throws Exception {
        return gitServiceQueue.listVersions(tenantId, branch, entityType, pageLink);
    }

    @Override
    public ListenableFuture<PageData<EntityVersion>> listVersions(TenantId tenantId, String branch, PageLink pageLink) throws Exception {
        return gitServiceQueue.listVersions(tenantId, branch, pageLink);
    }

    @Override
    public ListenableFuture<List<VersionedEntityInfo>> listEntitiesAtVersion(TenantId tenantId, String branch, String versionId, EntityType entityType) throws Exception {
        return gitServiceQueue.listEntitiesAtVersion(tenantId, branch, versionId, entityType);
    }

    @Override
    public ListenableFuture<List<VersionedEntityInfo>> listAllEntitiesAtVersion(TenantId tenantId, String branch, String versionId) throws Exception {
        return gitServiceQueue.listEntitiesAtVersion(tenantId, branch, versionId);
    }

    @SuppressWarnings({"UnstableApiUsage", "rawtypes"})
    @Override
    public UUID loadEntitiesVersion(SecurityUser user, VersionLoadRequest request) throws Exception {
        EntitiesImportCtx ctx = new EntitiesImportCtx(UUID.randomUUID(), user, request.getVersionId());
        cachePut(ctx.getRequestId(), VersionLoadResult.empty());
        switch (request.getType()) {
            case SINGLE_ENTITY: {
                SingleEntityVersionLoadRequest versionLoadRequest = (SingleEntityVersionLoadRequest) request;
                VersionLoadConfig config = versionLoadRequest.getConfig();
                ListenableFuture<EntityExportData> future = gitServiceQueue.getEntity(user.getTenantId(), request.getVersionId(), versionLoadRequest.getExternalEntityId());
                DonAsynchron.withCallback(future,
                        entityData -> doInTemplate(ctx, request, c -> loadSingleEntity(c, config, entityData)),
                        e -> processLoadError(ctx, e), executor);
                break;
            }
            case ENTITY_TYPE: {
                EntityTypeVersionLoadRequest versionLoadRequest = (EntityTypeVersionLoadRequest) request;
                executor.submit(() -> doInTemplate(ctx, request, c -> loadMultipleEntities(c, versionLoadRequest)));
                break;
            }
            default:
                throw new IllegalArgumentException("Unsupported version load request");
        }

        return ctx.getRequestId();
    }

    private <R> VersionLoadResult doInTemplate(EntitiesImportCtx ctx, VersionLoadRequest request, Function<EntitiesImportCtx, VersionLoadResult> function) {
        try {
            VersionLoadResult result = transactionTemplate.execute(status -> function.apply(ctx));
            for (ThrowingRunnable throwingRunnable : ctx.getEventCallbacks()) {
                throwingRunnable.run();
            }
            result.setDone(true);
            return cachePut(ctx.getRequestId(), result);
        } catch (LoadEntityException e) {
            return cachePut(ctx.getRequestId(), onError(e.getData(), e.getCause()));
        } catch (Exception e) {
            log.info("[{}] Failed to process request [{}] due to: ", ctx.getTenantId(), request, e);
            return cachePut(ctx.getRequestId(), VersionLoadResult.error(EntityLoadError.runtimeError(e.getMessage())));
        }
    }

    private VersionLoadResult loadSingleEntity(EntitiesImportCtx ctx, VersionLoadConfig config, EntityExportData entityData) {
        try {
            ctx.setSettings(EntityImportSettings.builder()
                    .updateRelations(config.isLoadRelations())
                    .saveAttributes(config.isLoadAttributes())
                    .saveCredentials(config.isLoadCredentials())
                    .findExistingByName(false)
                    .build());
            ctx.setFinalImportAttempt(true);
            EntityImportResult<?> importResult = exportImportService.importEntity(ctx, entityData);

            exportImportService.saveReferencesAndRelations(ctx);

            return VersionLoadResult.success(EntityTypeLoadResult.builder()
                    .entityType(importResult.getEntityType())
                    .created(importResult.getOldEntity() == null ? 1 : 0)
                    .updated(importResult.getOldEntity() != null ? 1 : 0)
                    .deleted(0)
                    .build());
        } catch (Exception e) {
            throw new LoadEntityException(entityData, e);
        }
    }

    @SneakyThrows
    private VersionLoadResult loadMultipleEntities(EntitiesImportCtx ctx, EntityTypeVersionLoadRequest request) {
        var sw = TbStopWatch.create("before");

        List<EntityType> entityTypes = request.getEntityTypes().keySet().stream()
                .sorted(exportImportService.getEntityTypeComparatorForImport()).collect(Collectors.toList());
        for (EntityType entityType : entityTypes) {
            log.debug("[{}] Loading {} entities", ctx.getTenantId(), entityType);
            sw.startNew("Entities " + entityType.name());
            ctx.setSettings(getEntityImportSettings(request, entityType));
            importEntities(ctx, entityType);
            persistToCache(ctx);
        }

        sw.startNew("Reimport");
        reimport(ctx);
        persistToCache(ctx);

        sw.startNew("Remove Others");
        request.getEntityTypes().keySet().stream()
                .filter(entityType -> request.getEntityTypes().get(entityType).isRemoveOtherEntities())
                .sorted(exportImportService.getEntityTypeComparatorForImport().reversed())
                .forEach(entityType -> removeOtherEntities(ctx, entityType));
        persistToCache(ctx);

        sw.startNew("References and Relations");
        exportImportService.saveReferencesAndRelations(ctx);

        sw.stop();
        for (var task : sw.getTaskInfo()) {
            log.info("[{}] Executed: {} in {}ms", ctx.getTenantId(), task.getTaskName(), task.getTimeMillis());
        }
        log.info("[{}] Total time: {}ms", ctx.getTenantId(), sw.getTotalTimeMillis());
        return VersionLoadResult.success(new ArrayList<>(ctx.getResults().values()));
    }

    private EntityImportSettings getEntityImportSettings(EntityTypeVersionLoadRequest request, EntityType entityType) {
        var config = request.getEntityTypes().get(entityType);
        return EntityImportSettings.builder()
                .updateRelations(config.isLoadRelations())
                .saveAttributes(config.isLoadAttributes())
                .saveCredentials(config.isLoadCredentials())
                .findExistingByName(config.isFindExistingEntityByName())
                .build();
    }

    @SneakyThrows
    @SuppressWarnings({"rawtypes", "unchecked"})
    private void importEntities(EntitiesImportCtx ctx, EntityType entityType) {
        int limit = 100;
        int offset = 0;
        List<EntityExportData> entityDataList;
        do {
            try {
                entityDataList = gitServiceQueue.getEntities(ctx.getTenantId(), ctx.getVersionId(), entityType, offset, limit).get();
            } catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException(e);
            }
            log.debug("[{}] Loading {} entities pack ({})", ctx.getTenantId(), entityType, entityDataList.size());
            for (EntityExportData entityData : entityDataList) {
                EntityExportData reimportBackup = JacksonUtil.clone(entityData);
                EntityImportResult<?> importResult;
                try {
                    importResult = exportImportService.importEntity(ctx, entityData);
                } catch (Exception e) {
                    throw new LoadEntityException(entityData, e);
                }
                if (!importResult.isUpdatedAllExternalIds()) {
                    ctx.getToReimport().put(entityData.getEntity().getExternalId(), new ReimportTask(reimportBackup, ctx.getSettings()));
                    continue;
                }

                registerResult(ctx, entityType, importResult);
                ctx.getImportedEntities().computeIfAbsent(entityType, t -> new HashSet<>())
                        .add(importResult.getSavedEntity().getId());
            }
            log.debug("Imported {} pack for tenant {}", entityType, ctx.getTenantId());
            offset += limit;
        } while (entityDataList.size() == limit);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void reimport(EntitiesImportCtx ctx) {
        ctx.setFinalImportAttempt(true);
        ctx.getToReimport().forEach((externalId, task) -> {
            try {
                EntityExportData entityData = task.getData();
                var settings = task.getSettings();
                ctx.setSettings(settings);
                EntityImportResult<?> importResult = exportImportService.importEntity(ctx, entityData);

                registerResult(ctx, externalId.getEntityType(), importResult);
                ctx.getImportedEntities().computeIfAbsent(externalId.getEntityType(), t -> new HashSet<>())
                        .add(importResult.getSavedEntity().getId());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    private void removeOtherEntities(EntitiesImportCtx ctx, EntityType entityType) {
        DaoUtil.processInBatches(pageLink -> {
            return exportableEntitiesService.findEntitiesByTenantId(ctx.getTenantId(), entityType, pageLink);
        }, 100, entity -> {
            if (ctx.getImportedEntities().get(entityType) == null || !ctx.getImportedEntities().get(entityType).contains(entity.getId())) {
                exportableEntitiesService.removeById(ctx.getTenantId(), entity.getId());

                ctx.addEventCallback(() -> {
                    entityNotificationService.notifyDeleteEntity(ctx.getTenantId(), entity.getId(),
                            entity, null, ActionType.DELETED, null, ctx.getUser());
                });
                ctx.registerDeleted(entityType);
            }
        });
    }

    private VersionLoadResult onError(EntityExportData<?> entityData, Throwable e) {
        return analyze(e, entityData).orElse(VersionLoadResult.error(EntityLoadError.runtimeError(e.getMessage())));
    }

    private Optional<VersionLoadResult> analyze(Throwable e, EntityExportData<?> entityData) {
        if (e == null) {
            return Optional.empty();
        } else {
            if (e instanceof DeviceCredentialsValidationException) {
                return Optional.of(VersionLoadResult.error(EntityLoadError.credentialsError(entityData.getExternalId())));
            } else if (e instanceof MissingEntityException) {
                return Optional.of(VersionLoadResult.error(EntityLoadError.referenceEntityError(entityData.getExternalId(), ((MissingEntityException) e).getEntityId())));
            } else {
                return analyze(e.getCause(), entityData);
            }
        }
    }

    @Override
    public ListenableFuture<EntityDataDiff> compareEntityDataToVersion(SecurityUser user, String branch, EntityId entityId, String versionId) throws Exception {
        HasId<EntityId> entity = exportableEntitiesService.findEntityByTenantIdAndId(user.getTenantId(), entityId);
        if (!(entity instanceof ExportableEntity)) throw new IllegalArgumentException("Unsupported entity type");

        EntityId externalId = ((ExportableEntity<EntityId>) entity).getExternalId();
        if (externalId == null) externalId = entityId;

        return transform(gitServiceQueue.getEntity(user.getTenantId(), versionId, externalId),
                otherVersion -> {
                    SimpleEntitiesExportCtx ctx = new SimpleEntitiesExportCtx(user, null, null, EntityExportSettings.builder()
                            .exportRelations(otherVersion.hasRelations())
                            .exportAttributes(otherVersion.hasAttributes())
                            .exportCredentials(otherVersion.hasCredentials())
                            .build());
                    EntityExportData<?> currentVersion;
                    try {
                        currentVersion = exportImportService.exportEntity(ctx, entityId);
                    } catch (ThingsboardException e) {
                        throw new RuntimeException(e);
                    }
                    return new EntityDataDiff(currentVersion.sort(), otherVersion.sort());
                }, MoreExecutors.directExecutor());
    }

    @Override
    public ListenableFuture<EntityDataInfo> getEntityDataInfo(SecurityUser user, EntityId entityId, String versionId) {
        return Futures.transform(gitServiceQueue.getEntity(user.getTenantId(), versionId, entityId),
                entity -> new EntityDataInfo(entity.hasRelations(), entity.hasAttributes(), entity.hasCredentials()), MoreExecutors.directExecutor());
    }


    @Override
    public ListenableFuture<List<String>> listBranches(TenantId tenantId) throws Exception {
        return gitServiceQueue.listBranches(tenantId);
    }

    @Override
    public RepositorySettings getVersionControlSettings(TenantId tenantId) {
        return repositorySettingsService.get(tenantId);
    }

    @Override
    public ListenableFuture<RepositorySettings> saveVersionControlSettings(TenantId tenantId, RepositorySettings versionControlSettings) {
        var restoredSettings = this.repositorySettingsService.restore(tenantId, versionControlSettings);
        try {
            var future = gitServiceQueue.initRepository(tenantId, restoredSettings);
            return Futures.transform(future, f -> repositorySettingsService.save(tenantId, restoredSettings), MoreExecutors.directExecutor());
        } catch (Exception e) {
            log.debug("{} Failed to init repository: {}", tenantId, versionControlSettings, e);
            throw new RuntimeException("Failed to init repository!", e);
        }
    }

    @Override
    public ListenableFuture<Void> deleteVersionControlSettings(TenantId tenantId) throws Exception {
        if (repositorySettingsService.delete(tenantId)) {
            return gitServiceQueue.clearRepository(tenantId);
        } else {
            return Futures.immediateFuture(null);
        }
    }

    @Override
    public ListenableFuture<Void> checkVersionControlAccess(TenantId tenantId, RepositorySettings settings) throws ThingsboardException {
        settings = this.repositorySettingsService.restore(tenantId, settings);
        try {
            return gitServiceQueue.testRepository(tenantId, settings);
        } catch (Exception e) {
            throw new ThingsboardException(String.format("Unable to access repository: %s", getCauseMessage(e)),
                    ThingsboardErrorCode.GENERAL);
        }
    }

    @Override
    public ListenableFuture<UUID> autoCommit(SecurityUser user, EntityId entityId) throws Exception {
        var repositorySettings = repositorySettingsService.get(user.getTenantId());
        if (repositorySettings == null) {
            return Futures.immediateFuture(null);
        }
        var autoCommitSettings = autoCommitSettingsService.get(user.getTenantId());
        if (autoCommitSettings == null) {
            return Futures.immediateFuture(null);
        }
        var entityType = entityId.getEntityType();
        AutoVersionCreateConfig autoCommitConfig = autoCommitSettings.get(entityType);
        if (autoCommitConfig == null) {
            return Futures.immediateFuture(null);
        }
        SingleEntityVersionCreateRequest vcr = new SingleEntityVersionCreateRequest();
        var autoCommitBranchName = autoCommitConfig.getBranch();
        if (StringUtils.isEmpty(autoCommitBranchName)) {
            autoCommitBranchName = StringUtils.isNotEmpty(repositorySettings.getDefaultBranch()) ? repositorySettings.getDefaultBranch() : "auto-commits";
        }
        vcr.setBranch(autoCommitBranchName);
        vcr.setVersionName("auto-commit at " + Instant.ofEpochSecond(System.currentTimeMillis() / 1000));
        vcr.setEntityId(entityId);
        vcr.setConfig(autoCommitConfig);
        return saveEntitiesVersion(user, vcr);
    }

    @Override
    public ListenableFuture<UUID> autoCommit(SecurityUser user, EntityType entityType, List<UUID> entityIds) throws Exception {
        var repositorySettings = repositorySettingsService.get(user.getTenantId());
        if (repositorySettings == null) {
            return Futures.immediateFuture(null);
        }
        var autoCommitSettings = autoCommitSettingsService.get(user.getTenantId());
        if (autoCommitSettings == null) {
            return Futures.immediateFuture(null);
        }
        AutoVersionCreateConfig autoCommitConfig = autoCommitSettings.get(entityType);
        if (autoCommitConfig == null) {
            return Futures.immediateFuture(null);
        }
        var autoCommitBranchName = autoCommitConfig.getBranch();
        if (StringUtils.isEmpty(autoCommitBranchName)) {
            autoCommitBranchName = StringUtils.isNotEmpty(repositorySettings.getDefaultBranch()) ? repositorySettings.getDefaultBranch() : "auto-commits";
        }
        ComplexVersionCreateRequest vcr = new ComplexVersionCreateRequest();
        vcr.setBranch(autoCommitBranchName);
        vcr.setVersionName("auto-commit at " + Instant.ofEpochSecond(System.currentTimeMillis() / 1000));
        vcr.setSyncStrategy(SyncStrategy.MERGE);

        EntityTypeVersionCreateConfig vcrConfig = new EntityTypeVersionCreateConfig();
        vcrConfig.setEntityIds(entityIds);
        vcr.setEntityTypes(Collections.singletonMap(entityType, vcrConfig));
        return saveEntitiesVersion(user, vcr);
    }

    private String getCauseMessage(Exception e) {
        String message;
        if (e.getCause() != null && StringUtils.isNotEmpty(e.getCause().getMessage())) {
            message = e.getCause().getMessage();
        } else {
            message = e.getMessage();
        }
        return message;
    }

    private void registerResult(EntitiesImportCtx ctx, EntityType entityType, EntityImportResult<?> importResult) {
        if (importResult.isCreated()) {
            ctx.registerResult(entityType, true);
        } else if (importResult.isUpdated() || importResult.isUpdatedRelatedEntities()) {
            ctx.registerResult(entityType, false);
        }
    }

    private void processCommitError(SecurityUser user, VersionCreateRequest request, CommitGitRequest commit, Throwable e) {
        log.debug("[{}] Failed to prepare the commit: {}", user.getId(), request, e);
        cachePut(commit.getTxId(), new VersionCreationResult(e.getMessage()));
    }

    private void processLoadError(EntitiesImportCtx ctx, Throwable e) {
        log.debug("[{}] Failed to load the commit: {}", ctx.getRequestId(), ctx.getVersionId(), e);
        cachePut(ctx.getRequestId(), VersionLoadResult.error(EntityLoadError.runtimeError(e.getMessage())));
    }

    private void cachePut(UUID requestId, VersionCreationResult result) {
        taskCache.put(requestId, VersionControlTaskCacheEntry.newForExport(result));
    }

    private VersionLoadResult cachePut(UUID requestId, VersionLoadResult result) {
        log.debug("[{}] Cache put: {}", requestId, result);
        taskCache.put(requestId, VersionControlTaskCacheEntry.newForImport(result));
        return result;
    }

    private void persistToCache(EntitiesImportCtx ctx) {
        cachePut(ctx.getRequestId(), VersionLoadResult.success(new ArrayList<>(ctx.getResults().values())));
    }

}
