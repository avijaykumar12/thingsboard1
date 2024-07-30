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
package org.thingsboard.server.common.data.alarm.rule.condition;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.io.Serial;

@Getter
@Schema
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class FromMessageArgument extends AbstractArgument {

    @Serial
    private static final long serialVersionUID = 2200958268935112889L;

    @Schema(description = "JSON object for specifying alarm condition by specific key")
    private final AlarmConditionFilterKey key;

    @JsonCreator
    public FromMessageArgument(@JsonProperty("key") AlarmConditionFilterKey key, @JsonProperty("valueType") ArgumentValueType valueType) {
        super(valueType);
        this.key = key;
    }

    public FromMessageArgument(AlarmConditionKeyType type, String key, ArgumentValueType valueType) {
        this(new AlarmConditionFilterKey(type, key), valueType);
    }

    @Override
    public ArgumentType getType() {
        return ArgumentType.FROM_MESSAGE;
    }

}
