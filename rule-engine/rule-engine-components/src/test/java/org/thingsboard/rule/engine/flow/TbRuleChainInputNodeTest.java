/**
 * Copyright © 2016-2024 The Thingsboard Authors
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
package org.thingsboard.rule.engine.flow;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.thingsboard.common.util.JacksonUtil;
import org.thingsboard.rule.engine.AbstractRuleNodeUpgradeTest;
import org.thingsboard.rule.engine.api.RuleEngineAssetProfileCache;
import org.thingsboard.rule.engine.api.RuleEngineDeviceProfileCache;
import org.thingsboard.rule.engine.api.TbContext;
import org.thingsboard.rule.engine.api.TbNode;
import org.thingsboard.rule.engine.api.TbNodeConfiguration;
import org.thingsboard.rule.engine.api.TbNodeException;
import org.thingsboard.server.common.data.DeviceProfile;
import org.thingsboard.server.common.data.asset.AssetProfile;
import org.thingsboard.server.common.data.id.AssetId;
import org.thingsboard.server.common.data.id.DeviceId;
import org.thingsboard.server.common.data.id.EntityId;
import org.thingsboard.server.common.data.id.RuleChainId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.msg.TbMsgType;
import org.thingsboard.server.common.data.rule.RuleChain;
import org.thingsboard.server.common.msg.TbMsg;
import org.thingsboard.server.common.msg.TbMsgMetaData;
import org.thingsboard.server.dao.rule.RuleChainService;

import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class TbRuleChainInputNodeTest extends AbstractRuleNodeUpgradeTest {

    private final DeviceId DEVICE_ID = new DeviceId(UUID.fromString("97731954-2147-4176-8f1a-d14f1b73e4e6"));
    private final AssetId ASSET_ID = new AssetId(UUID.fromString("841a47bd-4e8e-4ea5-88e6-420da0d70e51"));
    private final TenantId TENANT_ID = new TenantId(UUID.fromString("4ba69ea5-6b27-42df-ab66-e7a727a67027"));
    private TbRuleChainInputNode node;
    private TbRuleChainInputNodeConfiguration config;
    private TbNodeConfiguration nodeConfiguration;
    @Mock
    private TbContext ctxMock;
    @Mock
    private RuleChainService ruleChainServiceMock;
    @Mock
    private RuleEngineDeviceProfileCache deviceProfileCacheMock;
    @Mock
    private RuleEngineAssetProfileCache assetProfileCacheMock;

    @BeforeEach
    public void setUp() {
        node = spy(new TbRuleChainInputNode());
        config = new TbRuleChainInputNodeConfiguration().defaultConfiguration();
        nodeConfiguration = new TbNodeConfiguration(JacksonUtil.valueToTree(config));
    }

    @Test
    public void givenValidConfigWithRuleChainId_whenInit_thenOk() throws TbNodeException {
        //GIVEN
        String ruleChainId = "dfdebb47-c672-45ab-8795-97b6f9b3ce21";
        config.setRuleChainId(ruleChainId);
        nodeConfiguration = new TbNodeConfiguration(JacksonUtil.valueToTree(config));

        //WHEN
        assertThatCode(() -> node.init(ctxMock, nodeConfiguration))
                .doesNotThrowAnyException();

        //THEN
        verify(ctxMock).checkTenantEntity(eq(new RuleChainId(UUID.fromString(ruleChainId))));
    }

    @ParameterizedTest
    @ValueSource(strings = {"91acbce0-079fdb", "", "  ", "my test string"})
    public void givenInvalidRuleChainId_whenInit_thenThrowException(String ruleChainId) {
        //GIVEN
        config.setRuleChainId(ruleChainId);
        nodeConfiguration = new TbNodeConfiguration(JacksonUtil.valueToTree(config));

        //WHEN-THEN
        Assertions.assertThatThrownBy(() -> node.init(ctxMock, nodeConfiguration))
                .isInstanceOf(TbNodeException.class)
                .hasMessage("Failed to parse rule chain id: " + ruleChainId);
    }

    @Test
    public void givenForwardMsgToRootIsTrue_whenInit_thenOk() {
        //GIVEN
        RuleChain rootRuleChain = new RuleChain(
                new RuleChainId(UUID.fromString("77124ff7-1ca2-4ad2-bc65-cf860de249ea")));

        config.setForwardMsgToDefaultRuleChain(true);
        nodeConfiguration = new TbNodeConfiguration(JacksonUtil.valueToTree(config));

        when(ctxMock.getTenantId()).thenReturn(TENANT_ID);
        when(ctxMock.getRuleChainService()).thenReturn(ruleChainServiceMock);
        when(ruleChainServiceMock.getRootTenantRuleChain(any())).thenReturn(rootRuleChain);

        //WHEN
        assertThatCode(() -> node.init(ctxMock, nodeConfiguration))
                .doesNotThrowAnyException();

        //THEN
        verify(ctxMock).getTenantId();
        verify(ruleChainServiceMock).getRootTenantRuleChain(eq(TENANT_ID));
        verifyNoMoreInteractions(ctxMock, ruleChainServiceMock);
    }

    @Test
    public void givenForwardMsgToRootIsTrueAndNoTenantRootRuleChain_whenInit_thenThrowException() {
        //GIVEN
        config.setForwardMsgToDefaultRuleChain(true);
        nodeConfiguration = new TbNodeConfiguration(JacksonUtil.valueToTree(config));

        when(ctxMock.getTenantId()).thenReturn(TENANT_ID);
        when(ctxMock.getRuleChainService()).thenReturn(ruleChainServiceMock);
        when(ruleChainServiceMock.getRootTenantRuleChain(any())).thenReturn(null);

        //WHEN
        Assertions.assertThatThrownBy(() -> node.init(ctxMock, nodeConfiguration))
                .isInstanceOf(TbNodeException.class)
                .hasMessage("Failed to find root rule chain for tenant with id: " + TENANT_ID.getId());

        //THEN
        verify(ctxMock).getTenantId();
        verify(ruleChainServiceMock).getRootTenantRuleChain(eq(TENANT_ID));
        verifyNoMoreInteractions(ctxMock, ruleChainServiceMock);
    }

    @Test
    public void givenForwardMsgToRootIsTrue_whenOnMsg_thenShouldTransferToDeviceDefaultRuleChain() throws TbNodeException {
        //GIVEN
        RuleChain rootRuleChain = new RuleChain(
                new RuleChainId(UUID.fromString("3afd143b-b5c8-4aab-befa-b80ed5470f38")));
        RuleChain defaultRuleChain = new RuleChain(
                new RuleChainId(UUID.fromString("79acbce0-e789-11ee-9cf0-33d8b6079fba")));

        DeviceProfile deviceProfile = new DeviceProfile();
        deviceProfile.setDefaultRuleChainId(defaultRuleChain.getId());

        TbMsg msg = getMsg(DEVICE_ID);

        config.setForwardMsgToDefaultRuleChain(true);
        nodeConfiguration = new TbNodeConfiguration(JacksonUtil.valueToTree(config));

        when(ctxMock.getTenantId()).thenReturn(TENANT_ID);
        when(ctxMock.getRuleChainService()).thenReturn(ruleChainServiceMock);
        when(ruleChainServiceMock.getRootTenantRuleChain(any())).thenReturn(rootRuleChain);
        when(ctxMock.getDeviceProfileCache()).thenReturn(deviceProfileCacheMock);
        when(deviceProfileCacheMock.get(any(), any(DeviceId.class))).thenReturn(deviceProfile);

        node.init(ctxMock, nodeConfiguration);

        //WHEN
        node.onMsg(ctxMock, msg);

        //THEN
        ArgumentCaptor<RuleChainId> ruleChainArgumentCaptor = ArgumentCaptor.forClass(RuleChainId.class);
        verify(ctxMock).input(eq(msg), ruleChainArgumentCaptor.capture());
        RuleChainId expectedRuleChainId = ruleChainArgumentCaptor.getValue();
        assertThat(expectedRuleChainId).isNotEqualTo(rootRuleChain.getId())
                .isEqualTo(defaultRuleChain.getId());
    }

    @Test
    public void givenForwardMsgToRootIsTrue_whenOnMsg_thenShouldTransferToAssetDefaultRuleChain() throws TbNodeException {
        //GIVEN
        RuleChain rootRuleChain = new RuleChain(
                new RuleChainId(UUID.fromString("b2018fa9-d338-43bd-bfe3-92691d225ede")));
        RuleChain defaultRuleChain = new RuleChain(
                new RuleChainId(UUID.fromString("dc9d9989-93e0-4402-8ec6-726787688b4b")));

        AssetProfile assetProfile = new AssetProfile();
        assetProfile.setDefaultRuleChainId(defaultRuleChain.getId());

        TbMsg msg = getMsg(ASSET_ID);

        config.setForwardMsgToDefaultRuleChain(true);
        nodeConfiguration = new TbNodeConfiguration(JacksonUtil.valueToTree(config));

        when(ctxMock.getTenantId()).thenReturn(TENANT_ID);
        when(ctxMock.getRuleChainService()).thenReturn(ruleChainServiceMock);
        when(ruleChainServiceMock.getRootTenantRuleChain(any())).thenReturn(rootRuleChain);
        when(ctxMock.getAssetProfileCache()).thenReturn(assetProfileCacheMock);
        when(assetProfileCacheMock.get(any(), any(AssetId.class))).thenReturn(assetProfile);

        node.init(ctxMock, nodeConfiguration);

        //WHEN
        node.onMsg(ctxMock, msg);

        //THEN
        ArgumentCaptor<RuleChainId> ruleChainArgumentCaptor = ArgumentCaptor.forClass(RuleChainId.class);
        verify(ctxMock).input(eq(msg), ruleChainArgumentCaptor.capture());
        RuleChainId expectedRuleChainId = ruleChainArgumentCaptor.getValue();
        assertThat(expectedRuleChainId).isNotEqualTo(rootRuleChain.getId())
                .isEqualTo(defaultRuleChain.getId());
    }

    @Test
    public void givenForwardMsgToRootIsTrueWithoutDeviceDefaultRuleChain_whenOnMsg_thenShouldTransferToRootRuleChain() throws TbNodeException {
        //GIVEN
        RuleChain rootRuleChain = new RuleChain(
                new RuleChainId(UUID.fromString("6d87e06c-f2c7-4873-896e-a483dc280c0c")));

        DeviceProfile deviceProfile = new DeviceProfile();

        TbMsg msg = getMsg(DEVICE_ID);

        config.setForwardMsgToDefaultRuleChain(true);
        nodeConfiguration = new TbNodeConfiguration(JacksonUtil.valueToTree(config));

        when(ctxMock.getTenantId()).thenReturn(TENANT_ID);
        when(ctxMock.getRuleChainService()).thenReturn(ruleChainServiceMock);
        when(ruleChainServiceMock.getRootTenantRuleChain(any())).thenReturn(rootRuleChain);
        when(ctxMock.getDeviceProfileCache()).thenReturn(deviceProfileCacheMock);
        when(deviceProfileCacheMock.get(any(), any(DeviceId.class))).thenReturn(deviceProfile);

        node.init(ctxMock, nodeConfiguration);

        //WHEN
        node.onMsg(ctxMock, msg);

        //THEN
        ArgumentCaptor<RuleChainId> ruleChainArgumentCaptor = ArgumentCaptor.forClass(RuleChainId.class);
        verify(ctxMock).input(eq(msg), ruleChainArgumentCaptor.capture());
        assertThat(ruleChainArgumentCaptor.getValue()).isEqualTo(rootRuleChain.getId());
    }

    @Test
    public void givenForwardMsgToRootIsTrueWithoutAssetDefaultRuleChain_whenOnMsg_thenShouldTransferToRootRuleChain() throws TbNodeException {
        //GIVEN
        RuleChain rootRuleChain = new RuleChain(
                new RuleChainId(UUID.fromString("dc80f187-ede9-4e12-8209-badf116dd0f9")));

        AssetProfile assetProfile = new AssetProfile();

        TbMsg msg = getMsg(ASSET_ID);

        config.setForwardMsgToDefaultRuleChain(true);
        nodeConfiguration = new TbNodeConfiguration(JacksonUtil.valueToTree(config));

        when(ctxMock.getTenantId()).thenReturn(TENANT_ID);
        when(ctxMock.getRuleChainService()).thenReturn(ruleChainServiceMock);
        when(ruleChainServiceMock.getRootTenantRuleChain(any())).thenReturn(rootRuleChain);
        when(ctxMock.getAssetProfileCache()).thenReturn(assetProfileCacheMock);
        when(assetProfileCacheMock.get(any(), any(AssetId.class))).thenReturn(assetProfile);

        node.init(ctxMock, nodeConfiguration);

        //WHEN
        node.onMsg(ctxMock, msg);

        //THEN
        ArgumentCaptor<RuleChainId> ruleChainArgumentCaptor = ArgumentCaptor.forClass(RuleChainId.class);
        verify(ctxMock).input(eq(msg), ruleChainArgumentCaptor.capture());
        assertThat(ruleChainArgumentCaptor.getValue()).isEqualTo(rootRuleChain.getId());
    }

    @Test
    public void givenRuleChainId_whenOnMsg_thenShouldTransferToRuleChainById() throws TbNodeException {
        //GIVEN
        String ruleChainIdStr = "3c02c8b3-645c-4e67-aac5-f984f59471d1";
        RuleChain targetRuleChain = new RuleChain(new RuleChainId(UUID.fromString(ruleChainIdStr)));

        TbMsg msg = getMsg(DEVICE_ID);

        config.setRuleChainId(ruleChainIdStr);
        nodeConfiguration = new TbNodeConfiguration(JacksonUtil.valueToTree(config));

        node.init(ctxMock, nodeConfiguration);

        //WHEN
        node.onMsg(ctxMock, msg);

        //THEN
        ArgumentCaptor<RuleChainId> ruleChainArgumentCaptor = ArgumentCaptor.forClass(RuleChainId.class);
        verify(ctxMock).input(eq(msg), ruleChainArgumentCaptor.capture());
        assertThat(ruleChainArgumentCaptor.getValue()).isEqualTo(targetRuleChain.getId());
    }

    private static Stream<Arguments> givenFromVersionAndConfig_whenUpgrade_thenVerifyHasChangesAndConfig() {
        return Stream.of(
                //config for version 0
                Arguments.of(0,
                        "{\"ruleChainId\": null}",
                        true,
                        "{\"ruleChainId\": null, \"forwardMsgToDefaultRuleChain\": false}"
                ),
                //config for version 1 with upgrade from version 0
                Arguments.of(1,
                        "{\"ruleChainId\": null, \"forwardMsgToDefaultRuleChain\": false}",
                        false,
                        "{\"ruleChainId\": null, \"forwardMsgToDefaultRuleChain\": false}"
                )
        );
    }

    @Override
    protected TbNode getTestNode() {
        return node;
    }

    private TbMsg getMsg(EntityId entityId) {
        return TbMsg.newMsg(TbMsgType.POST_TELEMETRY_REQUEST, entityId, TbMsgMetaData.EMPTY, TbMsg.EMPTY_STRING);
    }
}
