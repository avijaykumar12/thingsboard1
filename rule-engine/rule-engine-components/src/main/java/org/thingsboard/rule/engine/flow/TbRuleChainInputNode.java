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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.thingsboard.rule.engine.api.RuleNode;
import org.thingsboard.rule.engine.api.TbContext;
import org.thingsboard.rule.engine.api.TbNode;
import org.thingsboard.rule.engine.api.TbNodeConfiguration;
import org.thingsboard.rule.engine.api.TbNodeException;
import org.thingsboard.rule.engine.api.util.TbNodeUtils;
import org.thingsboard.server.common.data.id.AssetId;
import org.thingsboard.server.common.data.id.DeviceId;
import org.thingsboard.server.common.data.id.RuleChainId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.plugin.ComponentType;
import org.thingsboard.server.common.data.rule.RuleChain;
import org.thingsboard.server.common.data.util.TbPair;
import org.thingsboard.server.common.msg.TbMsg;

import java.util.Optional;
import java.util.UUID;

@Slf4j
@RuleNode(
        type = ComponentType.FLOW,
        name = "rule chain",
        configClazz = TbRuleChainInputNodeConfiguration.class,
        version = 1,
        nodeDescription = "transfers the message to another rule chain",
        nodeDetails = "Allows to nest the rule chain similar to single rule node. " +
                "The incoming message is forwarded to the input node of the specified target rule chain. " +
                "The target rule chain may produce multiple labeled outputs. " +
                "You may use the outputs to forward the results of processing to other rule nodes.",
        uiResources = {"static/rulenode/rulenode-core-config.js"},
        configDirective = "tbFlowNodeRuleChainInputConfig",
        relationTypes = {},
        ruleChainNode = true,
        customRelations = true
)
public class TbRuleChainInputNode implements TbNode {

    private RuleChainId ruleChainId;
    private boolean forwardMsgToDefaultRuleChain;

    @Override
    public void init(TbContext ctx, TbNodeConfiguration configuration) throws TbNodeException {
        TbRuleChainInputNodeConfiguration config = TbNodeUtils.convert(configuration, TbRuleChainInputNodeConfiguration.class);
        this.forwardMsgToDefaultRuleChain = config.isForwardMsgToDefaultRuleChain();
        if (forwardMsgToDefaultRuleChain) {
            return;
        }
        UUID ruleChainUUID;
        try {
            ruleChainUUID = UUID.fromString(config.getRuleChainId());
        } catch (Exception e) {
            throw new TbNodeException("Failed to parse rule chain id: " + config.getRuleChainId(), true);
        }
        if (ruleChainUUID.equals(ctx.getSelf().getRuleChainId().getId())) {
            throw new TbNodeException("Forwarding messages to the current rule chain is not allowed!", true);
        }
        this.ruleChainId = new RuleChainId(ruleChainUUID);
        ctx.checkTenantEntity(ruleChainId);
    }

    @Override
    public void onMsg(TbContext ctx, TbMsg msg) throws TbNodeException {
        RuleChainId ruleChainId = this.ruleChainId;
        if (forwardMsgToDefaultRuleChain) {
            ruleChainId = getTargetRuleChainId(ctx, msg);
            if (ruleChainId.equals(ctx.getSelf().getRuleChainId())) {
                ctx.tellFailure(msg, new RuntimeException("Forwarding messages to the current rule chain is not allowed!"));
                return;
            }
        }
        ctx.input(msg, ruleChainId);
    }

    @Override
    public TbPair<Boolean, JsonNode> upgrade(int fromVersion, JsonNode oldConfiguration) throws TbNodeException {
        boolean hasChanges = false;
        switch (fromVersion) {
            case 0:
                if (!oldConfiguration.has("forwardMsgToDefaultRuleChain")) {
                    hasChanges = true;
                    ((ObjectNode) oldConfiguration).put("forwardMsgToDefaultRuleChain", false);
                }
                break;
            default:
                break;
        }
        return new TbPair<>(hasChanges, oldConfiguration);
    }

    private RuleChainId getTargetRuleChainId(TbContext ctx, TbMsg msg) throws TbNodeException {
        RuleChainId targetRuleChainId = switch (msg.getOriginator().getEntityType()) {
            case DEVICE ->
                    ctx.getDeviceProfileCache().get(ctx.getTenantId(), (DeviceId) msg.getOriginator()).getDefaultRuleChainId();
            case ASSET ->
                    ctx.getAssetProfileCache().get(ctx.getTenantId(), (AssetId) msg.getOriginator()).getDefaultRuleChainId();
            default -> null;
        };
        return Optional.ofNullable(targetRuleChainId).orElse(getRootRuleChainId(ctx));
    }

    private RuleChainId getRootRuleChainId(TbContext ctx) throws TbNodeException {
        TenantId currentTenantId = ctx.getTenantId();
        RuleChain rootTenantRuleChain = ctx.getRuleChainService().getRootTenantRuleChain(currentTenantId);
        if (rootTenantRuleChain == null) {
            throw new TbNodeException("Failed to find root rule chain for tenant with id: " + currentTenantId.getId(), true);
        }
        return rootTenantRuleChain.getId();
    }
}
