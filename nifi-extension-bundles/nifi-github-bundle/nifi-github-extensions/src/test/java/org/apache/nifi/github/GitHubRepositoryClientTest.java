/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.apache.nifi.github;

import org.junit.jupiter.api.Test;
import org.kohsuke.github.GHCommit;
import org.kohsuke.github.GHCompare;
import org.kohsuke.github.GHFileNotFoundException;
import org.kohsuke.github.GHUser;
import org.kohsuke.github.GitUser;

import java.io.IOException;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GitHubRepositoryClientTest {

    @Test
    void testResolveCommitAuthorReturnsLoginWhenAccountResolvable() throws IOException {
        final GHUser ghUser = mock(GHUser.class);
        when(ghUser.getLogin()).thenReturn("octocat");

        final GHCommit ghCommit = mock(GHCommit.class);
        when(ghCommit.getAuthor()).thenReturn(ghUser);

        final GHCommit.ShortInfo shortInfo = mock(GHCommit.ShortInfo.class);

        final String author = GitHubRepositoryClient.resolveCommitAuthor(ghCommit, shortInfo);
        assertEquals("octocat", author);
    }

    @Test
    void testResolveCommitAuthorFallsBackToGitAuthorWhenAccountNotFound() throws IOException {
        final GHCommit ghCommit = mock(GHCommit.class);
        when(ghCommit.getAuthor()).thenThrow(new GHFileNotFoundException("/users/Copilot 404 Not Found"));

        final GitUser gitAuthor = mock(GitUser.class);
        when(gitAuthor.getName()).thenReturn("Copilot");
        final GHCommit.ShortInfo shortInfo = mock(GHCommit.ShortInfo.class);
        when(shortInfo.getAuthor()).thenReturn(gitAuthor);

        final String author = GitHubRepositoryClient.resolveCommitAuthor(ghCommit, shortInfo);
        assertEquals("Copilot", author);
    }

    @Test
    void testResolveCommitAuthorFallsBackToGitAuthorWhenNoAssociatedAccount() throws IOException {
        final GHCommit ghCommit = mock(GHCommit.class);
        when(ghCommit.getAuthor()).thenReturn(null);

        final GitUser gitAuthor = mock(GitUser.class);
        when(gitAuthor.getName()).thenReturn("Jane Developer");
        final GHCommit.ShortInfo shortInfo = mock(GHCommit.ShortInfo.class);
        when(shortInfo.getAuthor()).thenReturn(gitAuthor);

        final String author = GitHubRepositoryClient.resolveCommitAuthor(ghCommit, shortInfo);
        assertEquals("Jane Developer", author);
    }

    @Test
    void testResolveCommitAuthorReturnsPlaceholderWhenNoAuthorAvailable() throws IOException {
        final GHCommit ghCommit = mock(GHCommit.class);
        when(ghCommit.getAuthor()).thenThrow(new GHFileNotFoundException("/users/Copilot 404 Not Found"));

        final GHCommit.ShortInfo shortInfo = mock(GHCommit.ShortInfo.class);
        when(shortInfo.getAuthor()).thenReturn(null);

        final String author = GitHubRepositoryClient.resolveCommitAuthor(ghCommit, shortInfo);
        assertEquals("unknown", author);
    }

    @Test
    void testGetChangedPathsRelativeToRepositoryPath() {
        final GHCompare compare = mockCompare(GHCompare.Status.ahead,
                mockFile("flows/bucket/changed.json", null),
                mockFile("flows/bucket/renamed.json", "flows/bucket/original.json"),
                mockFile("README.md", null));

        assertEquals(Optional.of(Set.of("bucket/changed.json", "bucket/renamed.json", "bucket/original.json")),
                GitHubRepositoryClient.getChangedPaths(compare, "flows"));
    }

    @Test
    void testGetChangedPathsWithoutRepositoryPath() {
        final GHCompare compare = mockCompare(GHCompare.Status.identical);

        assertEquals(Optional.of(Set.of()), GitHubRepositoryClient.getChangedPaths(compare, null));
    }

    @Test
    void testGetChangedPathsUnknownWhenHistoryDiverged() {
        final GHCompare compare = mockCompare(GHCompare.Status.diverged, mockFile("bucket/changed.json", null));

        assertEquals(Optional.empty(), GitHubRepositoryClient.getChangedPaths(compare, null));
    }

    @Test
    void testGetChangedPathsUnknownWhenFileListMayBeTruncated() {
        final GHCommit.File[] files = new GHCommit.File[GitHubRepositoryClient.COMPARE_FILES_LIMIT];
        for (int i = 0; i < files.length; i++) {
            files[i] = mockFile("bucket/flow-" + i + ".json", null);
        }

        assertEquals(Optional.empty(), GitHubRepositoryClient.getChangedPaths(mockCompare(GHCompare.Status.ahead, files), null));
    }

    private static GHCompare mockCompare(final GHCompare.Status status, final GHCommit.File... files) {
        final GHCompare compare = mock(GHCompare.class);
        when(compare.getStatus()).thenReturn(status);
        when(compare.getFiles()).thenReturn(files);
        return compare;
    }

    private static GHCommit.File mockFile(final String fileName, final String previousFileName) {
        final GHCommit.File file = mock(GHCommit.File.class);
        when(file.getFileName()).thenReturn(fileName);
        when(file.getPreviousFilename()).thenReturn(previousFileName);
        return file;
    }
}
