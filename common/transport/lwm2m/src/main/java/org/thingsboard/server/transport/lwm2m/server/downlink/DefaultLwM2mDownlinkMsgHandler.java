/**
 * Copyright © 2016-2021 The Thingsboard Authors
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
package org.thingsboard.server.transport.lwm2m.server.downlink;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.leshan.core.Link;
import org.eclipse.leshan.core.attributes.Attribute;
import org.eclipse.leshan.core.attributes.AttributeSet;
import org.eclipse.leshan.core.model.ResourceModel;
import org.eclipse.leshan.core.node.LwM2mPath;
import org.eclipse.leshan.core.node.LwM2mResource;
import org.eclipse.leshan.core.node.ObjectLink;
import org.eclipse.leshan.core.observation.Observation;
import org.eclipse.leshan.core.request.CompositeDownlinkRequest;
import org.eclipse.leshan.core.request.ContentFormat;
import org.eclipse.leshan.core.request.DeleteRequest;
import org.eclipse.leshan.core.request.DiscoverRequest;
import org.eclipse.leshan.core.request.DownlinkRequest;
import org.eclipse.leshan.core.request.ExecuteRequest;
import org.eclipse.leshan.core.request.ObserveRequest;
import org.eclipse.leshan.core.request.ReadCompositeRequest;
import org.eclipse.leshan.core.request.ReadRequest;
import org.eclipse.leshan.core.request.SimpleDownlinkRequest;
import org.eclipse.leshan.core.request.WriteAttributesRequest;
import org.eclipse.leshan.core.request.WriteCompositeRequest;
import org.eclipse.leshan.core.request.WriteRequest;
import org.eclipse.leshan.core.request.exception.InvalidRequestException;
import org.eclipse.leshan.core.response.DeleteResponse;
import org.eclipse.leshan.core.response.DiscoverResponse;
import org.eclipse.leshan.core.response.ExecuteResponse;
import org.eclipse.leshan.core.response.LwM2mResponse;
import org.eclipse.leshan.core.response.ObserveResponse;
import org.eclipse.leshan.core.response.ReadCompositeResponse;
import org.eclipse.leshan.core.response.ReadResponse;
import org.eclipse.leshan.core.response.WriteAttributesResponse;
import org.eclipse.leshan.core.response.WriteCompositeResponse;
import org.eclipse.leshan.core.response.WriteResponse;
import org.eclipse.leshan.core.util.Hex;
import org.eclipse.leshan.server.model.LwM2mModelProvider;
import org.eclipse.leshan.server.registration.Registration;
import org.springframework.stereotype.Service;
import org.thingsboard.common.util.JacksonUtil;
import org.thingsboard.server.common.data.device.data.lwm2m.ObjectAttributes;
import org.thingsboard.server.queue.util.TbLwM2mTransportComponent;
import org.thingsboard.server.transport.lwm2m.config.LwM2MTransportServerConfig;
import org.thingsboard.server.transport.lwm2m.server.LwM2mTransportContext;
import org.thingsboard.server.transport.lwm2m.server.client.LwM2mClient;
import org.thingsboard.server.transport.lwm2m.server.common.LwM2MExecutorAwareService;
import org.thingsboard.server.transport.lwm2m.server.downlink.composite.TbLwM2MReadCompositeRequest;
import org.thingsboard.server.transport.lwm2m.server.log.LwM2MTelemetryLogService;
import org.thingsboard.server.transport.lwm2m.server.rpc.composite.RpcWriteCompositeRequest;
import org.thingsboard.server.transport.lwm2m.utils.LwM2mValueConverterImpl;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static org.eclipse.leshan.core.attributes.Attribute.GREATER_THAN;
import static org.eclipse.leshan.core.attributes.Attribute.LESSER_THAN;
import static org.eclipse.leshan.core.attributes.Attribute.MAXIMUM_PERIOD;
import static org.eclipse.leshan.core.attributes.Attribute.MINIMUM_PERIOD;
import static org.eclipse.leshan.core.attributes.Attribute.STEP;
import static org.eclipse.leshan.core.model.ResourceModel.Type.OBJLNK;
import static org.eclipse.leshan.core.model.ResourceModel.Type.OPAQUE;
import static org.thingsboard.server.transport.lwm2m.server.LwM2mTransportUtil.convertMultiResourceValuesFromRpcBody;
import static org.thingsboard.server.transport.lwm2m.server.LwM2mTransportUtil.fromVersionedIdToObjectId;
import static org.thingsboard.server.transport.lwm2m.server.LwM2mTransportUtil.validateVersionedId;

@Slf4j
@Service
@TbLwM2mTransportComponent
@RequiredArgsConstructor
public class DefaultLwM2mDownlinkMsgHandler extends LwM2MExecutorAwareService implements LwM2mDownlinkMsgHandler {

    public LwM2mValueConverterImpl converter;

    private final LwM2mTransportContext context;
    private final LwM2MTransportServerConfig config;
    private final LwM2MTelemetryLogService logService;

    @PostConstruct
    public void init() {
        super.init();
        this.converter = LwM2mValueConverterImpl.getInstance();
    }

    @PreDestroy
    public void destroy() {
        super.destroy();
    }

    @Override
    protected int getExecutorSize() {
        return config.getDownlinkPoolSize();
    }

    @Override
    protected String getExecutorName() {
        return "LwM2M Downlink";
    }

    @Override
    public void sendReadRequest(LwM2mClient client, TbLwM2MReadRequest request, DownlinkRequestCallback<ReadRequest, ReadResponse> callback) {
        try {
            validateVersionedId(client, request);
            ReadRequest downlink = new ReadRequest(getReadRequestContentFormat(client, request, this.config.getModelProvider()), request.getObjectId());
            sendSimpleRequest(client, downlink, request.getTimeout(), callback);
        } catch (Exception e) {
            callback.onValidationError(request.toString(), e.getMessage());
        }
    }

    @Override
    public void sendReadCompositeRequest(LwM2mClient client, TbLwM2MReadCompositeRequest request, DownlinkRequestCallback<ReadCompositeRequest, ReadCompositeResponse> callback) {
        try {
            ContentFormat requestContentFormat = ContentFormat.SENML_JSON;
            ContentFormat responseContentFormat = ContentFormat.SENML_JSON;

            ReadCompositeRequest downlink = new ReadCompositeRequest(requestContentFormat, responseContentFormat, request.getObjectIds());
            sendCompositeRequest(client, downlink, this.config.getTimeout(), callback);
        } catch (InvalidRequestException e) {
            callback.onValidationError(request.toString(), e.getMessage());
        }
    }

    @Override
    public void sendObserveRequest(LwM2mClient client, TbLwM2MObserveRequest request, DownlinkRequestCallback<ObserveRequest, ObserveResponse> callback) {
        try {
            validateVersionedId(client, request);
            LwM2mPath resultIds = new LwM2mPath(request.getObjectId());
            Set<Observation> observations = context.getServer().getObservationService().getObservations(client.getRegistration());
            if (observations.stream().noneMatch(observation -> observation.getPath().equals(resultIds))) {
                ObserveRequest downlink;
                ContentFormat contentFormat = getReadRequestContentFormat(client, request, this.config.getModelProvider());
                if (resultIds.isResource()) {
                    downlink = new ObserveRequest(contentFormat, resultIds.getObjectId(), resultIds.getObjectInstanceId(), resultIds.getResourceId());
                } else if (resultIds.isObjectInstance()) {
                    downlink = new ObserveRequest(contentFormat, resultIds.getObjectId(), resultIds.getObjectInstanceId());
                } else {
                    downlink = new ObserveRequest(contentFormat, resultIds.getObjectId());
                }
                log.info("[{}] Send observation: {}.", client.getEndpoint(), request.getVersionedId());
                sendSimpleRequest(client, downlink, request.getTimeout(), callback);
            } else {
                callback.onValidationError(resultIds.toString(), "Observation is already registered!");
            }
        } catch (InvalidRequestException e) {
            callback.onValidationError(request.toString(), e.getMessage());
        }
    }

    @Override
    public void sendObserveAllRequest(LwM2mClient client, TbLwM2MObserveAllRequest request, DownlinkRequestCallback<TbLwM2MObserveAllRequest, Set<String>> callback) {
        Set<Observation> observations = context.getServer().getObservationService().getObservations(client.getRegistration());
        Set<String> paths = observations.stream().map(observation -> observation.getPath().toString()).collect(Collectors.toUnmodifiableSet());
        callback.onSuccess(request, paths);
    }

    @Override
    public void sendDiscoverAllRequest(LwM2mClient client, TbLwM2MDiscoverAllRequest request, DownlinkRequestCallback<TbLwM2MDiscoverAllRequest, List<Link>> callback) {
        callback.onSuccess(request, Arrays.asList(client.getRegistration().getSortedObjectLinks()));
    }

    @Override
    public void sendExecuteRequest(LwM2mClient client, TbLwM2MExecuteRequest request, DownlinkRequestCallback<ExecuteRequest, ExecuteResponse> callback) {
        try {
            validateVersionedId(client, request);
            ResourceModel resourceModelExecute = client.getResourceModel(request.getVersionedId(), this.config.getModelProvider());
            if (resourceModelExecute != null) {
                ExecuteRequest downlink;
                if (request.getParams() != null && !resourceModelExecute.multiple) {
                    downlink = new ExecuteRequest(request.getVersionedId(), (String) this.converter.convertValue(request.getParams(), resourceModelExecute.type, ResourceModel.Type.STRING, new LwM2mPath(request.getObjectId())));
                } else {
                    downlink = new ExecuteRequest(request.getVersionedId());
                }
                sendSimpleRequest(client, downlink, request.getTimeout(), callback);
            }
        } catch (InvalidRequestException e) {
            callback.onValidationError(request.toString(), e.getMessage());
        }
    }

    @Override
    public void sendDeleteRequest(LwM2mClient client, TbLwM2MDeleteRequest request, DownlinkRequestCallback<DeleteRequest, DeleteResponse> callback) {
        try {
            validateVersionedId(client, request);
            sendSimpleRequest(client, new DeleteRequest(request.getObjectId()), request.getTimeout(), callback);
        } catch (InvalidRequestException e) {
            callback.onValidationError(request.toString(), e.getMessage());
        }
    }

    @Override
    public void sendCancelObserveRequest(LwM2mClient client, TbLwM2MCancelObserveRequest request, DownlinkRequestCallback<TbLwM2MCancelObserveRequest, Integer> callback) {
        validateVersionedId(client, request);
        int observeCancelCnt = context.getServer().getObservationService().cancelObservations(client.getRegistration(), request.getObjectId());
        callback.onSuccess(request, observeCancelCnt);
    }

    @Override
    public void sendCancelAllRequest(LwM2mClient client, TbLwM2MCancelAllRequest request, DownlinkRequestCallback<TbLwM2MCancelAllRequest, Integer> callback) {
        int observeCancelCnt = context.getServer().getObservationService().cancelObservations(client.getRegistration());
        callback.onSuccess(request, observeCancelCnt);
    }

    @Override
    public void sendDiscoverRequest(LwM2mClient client, TbLwM2MDiscoverRequest request, DownlinkRequestCallback<DiscoverRequest, DiscoverResponse> callback) {
        validateVersionedId(client, request);
        sendSimpleRequest(client, new DiscoverRequest(request.getObjectId()), request.getTimeout(), callback);
    }

    @Override
    public void sendWriteAttributesRequest(LwM2mClient client, TbLwM2MWriteAttributesRequest request, DownlinkRequestCallback<WriteAttributesRequest, WriteAttributesResponse> callback) {
        try {
            validateVersionedId(client, request);
            if (request.getAttributes() == null) {
                throw new IllegalArgumentException("Attributes to write are not specified!");
            }
            ObjectAttributes params = request.getAttributes();
            List<Attribute> attributes = new LinkedList<>();
//            Dimension and Object version are read only attributes.
//            addAttribute(attributes, DIMENSION, params.getDim(), dim -> dim >= 0 && dim <= 255);
//            addAttribute(attributes, OBJECT_VERSION, params.getVer(), StringUtils::isNotEmpty, Function.identity());
            addAttribute(attributes, MAXIMUM_PERIOD, params.getPmax());
            addAttribute(attributes, MINIMUM_PERIOD, params.getPmin());
            addAttribute(attributes, GREATER_THAN, params.getGt());
            addAttribute(attributes, LESSER_THAN, params.getLt());
            addAttribute(attributes, STEP, params.getSt());
            AttributeSet attributeSet = new AttributeSet(attributes);
            sendSimpleRequest(client, new WriteAttributesRequest(request.getObjectId(), attributeSet), request.getTimeout(), callback);
        } catch (InvalidRequestException e) {
            callback.onValidationError(request.toString(), e.getMessage());
        }
    }

    @Override
    public void sendWriteReplaceRequest(LwM2mClient client, TbLwM2MWriteReplaceRequest request, DownlinkRequestCallback<WriteRequest, WriteResponse> callback) {
        LwM2mPath resultIds = new LwM2mPath(request.getObjectId());
        if (resultIds.isResource() || resultIds.isResourceInstance()) {
            validateVersionedId(client, request);
            ResourceModel resourceModelWrite = client.getResourceModel(request.getVersionedId(), this.config.getModelProvider());
            if (resourceModelWrite != null) {
                ContentFormat contentFormat = getWriteRequestContentFormat(client, request, this.config.getModelProvider());
                try {
                    WriteRequest downlink = null;
                    if (resourceModelWrite.multiple) {
                        if (request.getValue() instanceof Map && ((Map) request.getValue()).size() > 0) {
                            downlink = new WriteRequest(contentFormat, resultIds.getObjectId(), resultIds.getObjectInstanceId(), resultIds.getResourceId(),
                                    (Map<Integer, ?>) request.getValue(), resourceModelWrite.type);
                        } else {
                            callback.onValidationError(JacksonUtil.toString(request), "Resource value is bad. Format: " + request.getValue().getClass().getSimpleName() + ". Value of Multi-Instance Resource must be in Json format!");
                        }
                    } else {
                        downlink = this.getWriteRequestSingleResource(resourceModelWrite.type, contentFormat,
                                resultIds.getObjectId(), resultIds.getObjectInstanceId(), resultIds.getResourceId(), request.getValue());
                    }
                    if (downlink != null) {
                        sendSimpleRequest(client, downlink, request.getTimeout(), callback);
                    } else {
                        callback.onValidationError(JacksonUtil.toString(request), "WriteRequest is null.");
                    }
                } catch (Exception e) {
                    callback.onError(JacksonUtil.toString(request), e);
                }

            } else {
                callback.onValidationError(JacksonUtil.toString(request), "Resource " + request.getVersionedId() + " is not configured in the device profile!");
            }
        } else {
            callback.onValidationError(JacksonUtil.toString(request), "Resource " + request.getVersionedId() + ". This operation can only be used for Resource or ResourceInstance!");
        }
    }

    @Override
    public void sendWriteCompositeRequest(LwM2mClient client, RpcWriteCompositeRequest rpcWriteCompositeRequest, DownlinkRequestCallback<WriteCompositeRequest, WriteCompositeResponse> callback) {
        try {
            ContentFormat contentFormat = ContentFormat.SENML_JSON;
            try {
                WriteCompositeRequest downlink = new WriteCompositeRequest(contentFormat, rpcWriteCompositeRequest.getNodes());
                sendWriteCompositeRequest(client, downlink, this.config.getTimeout(), callback);
            } catch (Exception e) {
                callback.onError(JacksonUtil.toString(rpcWriteCompositeRequest), e);
            }
        } catch (InvalidRequestException e) {
            callback.onValidationError(rpcWriteCompositeRequest.toString(), e.getMessage());
        }
    }

    @Override
    public void sendWriteUpdateRequest(LwM2mClient client, TbLwM2MWriteUpdateRequest request, DownlinkRequestCallback<WriteRequest, WriteResponse> callback) {
        try {
            validateVersionedId(client, request);
            WriteRequest downlink = null;
            LwM2mPath resultIds = new LwM2mPath(request.getObjectId());
            ContentFormat contentFormat = getWriteRequestContentFormat(client, request, this.config.getModelProvider());
            if (resultIds.isObjectInstance()) {
                /*
                 *  params = "{\"id\":0,\"value\":[{\"id\":14,\"value\":\"+5\"},{\"id\":15,\"value\":\"+9\"}]}"
                 *  int rscId = resultIds.getObjectInstanceId();
                 *  contentFormat – Format of the payload (TLV or JSON).
                 */
                Collection<LwM2mResource> resources = client.getNewResourcesForInstance(request.getVersionedId(), request.getValue(), this.config.getModelProvider(), this.converter);
                if (resources.size() > 0) {
                    downlink = new WriteRequest(WriteRequest.Mode.UPDATE, contentFormat, resultIds.getObjectId(), resultIds.getObjectInstanceId(), resources);
                } else {
                    callback.onValidationError(JacksonUtil.toString(request), "No resources to update!");
                }
            }
            else if (resultIds.isResource()) {
                ResourceModel resourceModelWrite = client.getResourceModel(request.getVersionedId(), this.config.getModelProvider());
                if (resourceModelWrite.multiple) {
                    if (request.getValue() instanceof Map && ((Map) request.getValue()).size() > 0) {
                        Map value = convertMultiResourceValuesFromRpcBody((LinkedHashMap) request.getValue(), resourceModelWrite.type, request.getObjectId());
                        downlink = new WriteRequest(WriteRequest.Mode.UPDATE, contentFormat, resultIds.getObjectId(), resultIds.getObjectInstanceId(), resultIds.getResourceId(),
                                value, resourceModelWrite.type);
                    } else {
                        callback.onValidationError(JacksonUtil.toString(request), "Resource value is bad. Format: " + request.getValue().getClass().getSimpleName() + ". Value of Multi-Instance Resource must be in Json format!");
                    }
                }
            }
            if (downlink != null) {
                sendSimpleRequest(client, downlink, request.getTimeout(), callback);
            } else {
                callback.onValidationError(JacksonUtil.toString(request), "Resource " + request.getVersionedId() + ". This operation can only be used for ObjectInstance or Multi-Instance Resource !");
            }
        } catch (Exception e) {
            callback.onValidationError(request.toString(), e.getMessage());
        }
    }


    private <R extends SimpleDownlinkRequest<T>, T extends LwM2mResponse> void sendSimpleRequest(LwM2mClient client, R request, long timeoutInMs, DownlinkRequestCallback<R, T> callback) {
        sendRequest(client, request, timeoutInMs, callback, r -> request.getPath().toString());
    }

    private <R extends CompositeDownlinkRequest<T>, T extends LwM2mResponse> void sendCompositeRequest(LwM2mClient client, R request, long timeoutInMs, DownlinkRequestCallback<R, T> callback) {
        sendRequest(client, request, timeoutInMs, callback, r -> request.getPaths().toString());
    }

    private <R extends DownlinkRequest<T>, T extends LwM2mResponse> void sendRequest(LwM2mClient client, R request, long timeoutInMs, DownlinkRequestCallback<R, T> callback, Function<R, String> pathToStringFunction) {
        Registration registration = client.getRegistration();
        try {
            logService.log(client, String.format("[%s][%s] Sending request: %s to %s", registration.getId(), registration.getSocketAddress(), request.getClass().getSimpleName(), pathToStringFunction.apply(request)));
            context.getServer().send(registration, request, timeoutInMs, response -> {
                executor.submit(() -> {
                    try {
                        callback.onSuccess(request, response);
                    } catch (Exception e) {
                        log.error("[{}] failed to process successful response [{}] ", registration.getEndpoint(), response, e);
                    }
                });
            }, e -> {
                executor.submit(() -> {
                    callback.onError(JacksonUtil.toString(request), e);
                });
            });
        } catch (Exception e) {
            callback.onError(JacksonUtil.toString(request), e);
        }
    }

    private <R extends SimpleDownlinkRequest<T>, T extends LwM2mResponse> void sendWriteCompositeRequest(LwM2mClient client, WriteCompositeRequest request, long timeoutInMs,
                                                                                                         DownlinkRequestCallback<WriteCompositeRequest, WriteCompositeResponse> callback) {
        Registration registration = client.getRegistration();
        try {
            logService.log(client, String.format("[%s][%s] Sending request: %s to %s", registration.getId(), registration.getSocketAddress(), request.getClass().getSimpleName(), request.getPaths()));
            context.getServer().send(registration, request, timeoutInMs, response -> {
                executor.submit(() -> {
                    try {
                        if (response.isSuccess()) {
                            callback.onSuccess(request, response);
                        } else {
                            callback.onValidationError(request.getNodes().values().toString(), response.getErrorMessage());
                        }
                    } catch (Exception e) {
                        log.error("[{}] failed to process successful response [{}] ", registration.getEndpoint(), response, e);
                    }
                });
            }, e -> {
                executor.submit(() -> {
                    callback.onError(JacksonUtil.toString(request), e);
                });
            });
        } catch (Exception e) {
            callback.onError(JacksonUtil.toString(request), e);
        }
    }

    private WriteRequest getWriteRequestSingleResource(ResourceModel.Type type, ContentFormat contentFormat, int objectId, int instanceId, int resourceId, Object value) {
        switch (type) {
            case STRING:    // String
                return new WriteRequest(contentFormat, objectId, instanceId, resourceId, value.toString());
            case INTEGER:   // Long
                final long valueInt = Integer.toUnsignedLong(Integer.parseInt(value.toString()));
                return new WriteRequest(contentFormat, objectId, instanceId, resourceId, valueInt);
            case OBJLNK:    // ObjectLink
                return new WriteRequest(contentFormat, objectId, instanceId, resourceId, ObjectLink.fromPath(value.toString()));
            case BOOLEAN:   // Boolean
                return new WriteRequest(contentFormat, objectId, instanceId, resourceId, Boolean.parseBoolean(value.toString()));
            case FLOAT:     // Double
                return new WriteRequest(contentFormat, objectId, instanceId, resourceId, Double.parseDouble(value.toString()));
            case TIME:      // Date
                Date date = new Date(Long.decode(value.toString()));
                return new WriteRequest(contentFormat, objectId, instanceId, resourceId, date);
            case OPAQUE:    // byte[] value, base64
                byte[] valueRequest;
                if (value instanceof byte[]) {
                    valueRequest = (byte[]) value;
                } else {
                    valueRequest = Hex.decodeHex(value.toString().toCharArray());
                }
                return new WriteRequest(contentFormat, objectId, instanceId, resourceId, valueRequest);
            default:
                throw new IllegalArgumentException("Not supported type:" + type.name());
        }
    }

    private static <T> void addAttribute(List<Attribute> attributes, String attributeName, T value) {
        addAttribute(attributes, attributeName, value, null, null);
    }

    private static <T> void addAttribute(List<Attribute> attributes, String attributeName, T value, Function<T, ?> converter) {
        addAttribute(attributes, attributeName, value, null, converter);
    }

    private static <T> void addAttribute(List<Attribute> attributes, String attributeName, T value, Predicate<T> filter, Function<T, ?> converter) {
        if (value != null && ((filter == null) || filter.test(value))) {
            attributes.add(new Attribute(attributeName, converter != null ? converter.apply(value) : value));
        }
    }

    private static ContentFormat getReadRequestContentFormat(LwM2mClient client, HasContentFormat request, LwM2mModelProvider modelProvider) {
        if (request.getRequestContentFormat() != null) {
            return request.getRequestContentFormat();
        } else {
            String versionedId = null;
            if (request instanceof TbLwM2MReadRequest) {
                versionedId = ((TbLwM2MReadRequest) request).getVersionedId();
            } else if (request instanceof TbLwM2MObserveRequest) {
                return ContentFormat.JSON;
            }
            return getRequestContentFormat(client, versionedId, modelProvider);
        }
    }

    private static ContentFormat getWriteRequestContentFormat(LwM2mClient client, TbLwM2MDownlinkRequest request, LwM2mModelProvider modelProvider) {
        if (request instanceof TbLwM2MWriteReplaceRequest && ((TbLwM2MWriteReplaceRequest) request).getContentFormat() != null) {
            return ((TbLwM2MWriteReplaceRequest) request).getContentFormat();
        } else if (request instanceof TbLwM2MWriteUpdateRequest && ((TbLwM2MWriteUpdateRequest) request).getObjectContentFormat() != null) {
            return ((TbLwM2MWriteUpdateRequest) request).getObjectContentFormat();
        } else {
            String versionedId = null;
            if (request instanceof TbLwM2MWriteReplaceRequest) {
                versionedId = ((TbLwM2MWriteReplaceRequest) request).getVersionedId();
            } else if (request instanceof TbLwM2MWriteUpdateRequest) {
                versionedId = ((TbLwM2MWriteUpdateRequest) request).getVersionedId();
            }
            return getRequestContentFormat(client, versionedId, modelProvider);
        }
    }

    private static ContentFormat getRequestContentFormat(LwM2mClient client, String versionedId, LwM2mModelProvider modelProvider) {
        LwM2mPath pathIds = new LwM2mPath(fromVersionedIdToObjectId(versionedId));
        if (pathIds.isResource() || pathIds.isResourceInstance()) {
            ResourceModel resourceModel = client.getResourceModel(versionedId, modelProvider);
            if (resourceModel != null) {
                if ((resourceModel.multiple) || !pathIds.isResourceInstance()) {
                    return client.getDefaultContentFormat().equals(ContentFormat.TEXT) ? ContentFormat.JSON : client.getDefaultContentFormat();
                } else if (OBJLNK.equals(resourceModel.type)) {
                    return ContentFormat.LINK;
                } else if (OPAQUE.equals(resourceModel.type)) {
                    return ContentFormat.OPAQUE;
                }
            }
        }
        return client.getDefaultContentFormat().equals(ContentFormat.TEXT) ? ContentFormat.JSON : client.getDefaultContentFormat();
    }
}
