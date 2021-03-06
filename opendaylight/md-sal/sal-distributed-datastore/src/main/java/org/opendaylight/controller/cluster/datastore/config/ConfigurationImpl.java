/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.controller.cluster.datastore.config;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.opendaylight.controller.cluster.datastore.shardstrategy.ShardStrategy;
import org.opendaylight.controller.cluster.datastore.shardstrategy.ShardStrategyFactory;

public class ConfigurationImpl implements Configuration {
    private volatile Map<String, ModuleConfig> moduleConfigMap;

    // Look up maps to speed things up

    private volatile Map<String, String> namespaceToModuleName;
    private volatile Set<String> allShardNames;

    public ConfigurationImpl(final String moduleShardsConfigPath, final String modulesConfigPath) {
        this(new FileModuleShardConfigProvider(moduleShardsConfigPath, modulesConfigPath));
    }

    public ConfigurationImpl(final ModuleShardConfigProvider provider) {
        this.moduleConfigMap = ImmutableMap.copyOf(provider.retrieveModuleConfigs(this));

        this.allShardNames = createAllShardNames(moduleConfigMap.values());
        this.namespaceToModuleName = createNamespaceToModuleName(moduleConfigMap.values());
    }

    private static Set<String> createAllShardNames(Iterable<ModuleConfig> moduleConfigs) {
        final ImmutableSet.Builder<String> builder = ImmutableSet.builder();
        for(ModuleConfig moduleConfig : moduleConfigs) {
            builder.addAll(moduleConfig.getShardNames());
        }

        return builder.build();
    }

    private static Map<String, String> createNamespaceToModuleName(Iterable<ModuleConfig> moduleConfigs) {
        final ImmutableMap.Builder<String, String> builder = ImmutableMap.builder();
        for(ModuleConfig moduleConfig : moduleConfigs) {
            if(moduleConfig.getNameSpace() != null) {
                builder.put(moduleConfig.getNameSpace(), moduleConfig.getName());
            }
        }

        return builder.build();
    }

    @Override
    public Collection<String> getMemberShardNames(final String memberName){
        Preconditions.checkNotNull(memberName, "memberName should not be null");

        List<String> shards = new ArrayList<>();
        for(ModuleConfig moduleConfig: moduleConfigMap.values()) {
            for(ShardConfig shardConfig: moduleConfig.getShardConfigs()) {
                if(shardConfig.getReplicas().contains(memberName)) {
                    shards.add(shardConfig.getName());
                }
            }
        }

        return shards;
    }

    @Override
    public String getModuleNameFromNameSpace(final String nameSpace) {
        Preconditions.checkNotNull(nameSpace, "nameSpace should not be null");

        return namespaceToModuleName.get(nameSpace);
    }

    @Override
    public ShardStrategy getStrategyForModule(String moduleName) {
        Preconditions.checkNotNull(moduleName, "moduleName should not be null");

        ModuleConfig moduleConfig = moduleConfigMap.get(moduleName);
        return moduleConfig != null ? moduleConfig.getShardStrategy(): null;
    }

    @Override
    public String getShardNameForModule(final String moduleName) {
        Preconditions.checkNotNull(moduleName, "moduleName should not be null");

        ModuleConfig moduleConfig = moduleConfigMap.get(moduleName);
        Collection<ShardConfig> shardConfigs = moduleConfig != null ? moduleConfig.getShardConfigs() :
            Collections.<ShardConfig>emptySet();
        return !shardConfigs.isEmpty() ? shardConfigs.iterator().next().getName(): null;
    }

    @Override
    public Collection<String> getMembersFromShardName(final String shardName) {
        Preconditions.checkNotNull(shardName, "shardName should not be null");

        for(ModuleConfig moduleConfig: moduleConfigMap.values()) {
            ShardConfig shardConfig = moduleConfig.getShardConfig(shardName);
            if(shardConfig != null) {
                return shardConfig.getReplicas();
            }
        }

        return Collections.emptyList();
    }

    @Override
    public Set<String> getAllShardNames() {
        return allShardNames;
    }

    @Override
    public Collection<String> getUniqueMemberNamesForAllShards() {
        Set<String> allNames = new HashSet<>();
        for(String shardName: getAllShardNames()) {
            allNames.addAll(getMembersFromShardName(shardName));
        }

        return allNames;
    }

    @Override
    public synchronized void addModuleShardConfiguration(ModuleShardConfiguration config) {
        Preconditions.checkNotNull(config, "ModuleShardConfiguration should not be null");

        ModuleConfig moduleConfig = new ModuleConfig(config.getModuleName());
        moduleConfig.setNameSpace(config.getNamespace().toASCIIString());
        moduleConfig.setShardStrategy(createShardStrategy(config.getModuleName(), config.getShardStrategyName()));

        moduleConfig.addShardConfig(config.getShardName(), ImmutableSet.copyOf(config.getShardMemberNames()));

        moduleConfigMap = ImmutableMap.<String, ModuleConfig>builder().putAll(moduleConfigMap).
                put(config.getModuleName(), moduleConfig).build();

        namespaceToModuleName = ImmutableMap.<String, String>builder().putAll(namespaceToModuleName).
                put(moduleConfig.getNameSpace(), moduleConfig.getName()).build();
        allShardNames = ImmutableSet.<String>builder().addAll(allShardNames).add(config.getShardName()).build();
    }

    private ShardStrategy createShardStrategy(String moduleName, String shardStrategyName) {
        return ShardStrategyFactory.newShardStrategyInstance(moduleName, shardStrategyName, this);
    }
}
