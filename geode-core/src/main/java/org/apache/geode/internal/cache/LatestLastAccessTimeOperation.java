/*
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license
 * agreements. See the NOTICE file distributed with this work for additional information regarding
 * copyright ownership. The ASF licenses this file to You under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package org.apache.geode.internal.cache;

import java.util.Set;

import org.apache.geode.CancelException;
import org.apache.geode.distributed.internal.DM;
import org.apache.geode.distributed.internal.ReplyException;
import org.apache.geode.distributed.internal.membership.InternalDistributedMember;
import org.apache.geode.internal.Version;

/**
 * Operation that determines the latest last access time for a given region and key
 *
 * @since Geode 1.4
 */
public class LatestLastAccessTimeOperation<K> {
  private final InternalDistributedRegion<K, ?> region;
  private final K key;

  public LatestLastAccessTimeOperation(InternalDistributedRegion<K, ?> region, K key) {
    this.region = region;
    this.key = key;
  }

  public long getLatestLastAccessTime() {
    final Set<InternalDistributedMember> recipients =
        this.region.getCacheDistributionAdvisor().adviseInitializedReplicates();
    final DM dm = this.region.getDistributionManager();
    dm.retainMembersWithSameOrNewerVersion(recipients, Version.GEODE_140);
    final LatestLastAccessTimeReplyProcessor replyProcessor =
        new LatestLastAccessTimeReplyProcessor(dm, recipients);
    dm.putOutgoing(
        new LatestLastAccessTimeMessage<>(replyProcessor, recipients, this.region, this.key));
    try {
      replyProcessor.waitForReplies();
    } catch (ReplyException e) {
      if (!(e.getCause() instanceof CancelException)) {
        throw e;
      }
    } catch (InterruptedException e) {
      dm.getCancelCriterion().checkCancelInProgress(e);
      Thread.currentThread().interrupt();
    }
    return replyProcessor.getLatestLastAccessTime();
  }
}
