/*
 * Copyright 2014-2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, 
 * either express or implied. See the License for the specific language
 * governing permissions and limitations under the License.
 */
package org.dbflute.logic.replaceschema.process.altercheck.agent;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.dbflute.DfBuildProperties;
import org.dbflute.exception.DfAlterCheckRollbackSchemaFailureException;
import org.dbflute.helper.io.compress.DfZipArchiver;
import org.dbflute.helper.message.ExceptionMessageBuilder;
import org.dbflute.logic.replaceschema.process.altercheck.player.DfAlterCoreProcessPlayer;
import org.dbflute.properties.DfReplaceSchemaProperties;
import org.dbflute.util.DfCollectionUtil;
import org.dbflute.util.Srl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author jflute
 * @since 1.2.1 (2019/09/12 Thursday at sky-high) from DfAlterCheckProcess
 */
public class DfPreviousDBAgent {

    // ===================================================================================
    //                                                                          Definition
    //                                                                          ==========
    /** The logger instance for this class. (NotNull) */
    private static final Logger _log = LoggerFactory.getLogger(DfPreviousDBAgent.class);

    // ===================================================================================
    //                                                                           Attribute
    //                                                                           =========
    // -----------------------------------------------------
    //                                        Basic Resource
    //                                        --------------
    protected final DfAlterCoreProcessPlayer _coreProcessPlayer; // not null

    // -----------------------------------------------------
    //                                             Supporter
    //                                             ---------
    protected final DfAlterControlAgent _alterControlAgent;

    // ===================================================================================
    //                                                                         Constructor
    //                                                                         ===========
    public DfPreviousDBAgent(DfAlterCoreProcessPlayer coreProcessPlayer) {
        _coreProcessPlayer = coreProcessPlayer;
        _alterControlAgent = new DfAlterControlAgent();
    }

    // ===================================================================================
    //                                                                  PreviousDB Command
    //                                                                  ==================
    // -----------------------------------------------------
    //                                       Previous Schema
    //                                       ---------------
    public void playPreviousSchema() {
        _coreProcessPlayer.playPrevious(getMigrationPreviousDir());
    }

    // -----------------------------------------------------
    //                                       PreviousNG Mark
    //                                       ---------------
    public void markPreviousNG(String notice) {
        _alterControlAgent.deletePreviousOKMark();
        final String ngMark = getMigrationPreviousNGMark();
        try {
            final File markFile = new File(ngMark);
            if (!markFile.exists()) {
                _log.info("...Marking previous-NG: " + ngMark);
                markFile.createNewFile();
                _alterControlAgent.writeNotice(markFile, notice);
            }
        } catch (IOException e) {
            String msg = "Failed to create a file for previous-NG mark: " + ngMark;
            throw new IllegalStateException(msg, e);
        }
    }

    public void throwAlterCheckRollbackSchemaFailureException(RuntimeException e) {
        final ExceptionMessageBuilder br = new ExceptionMessageBuilder();
        br.addNotice(getAlterCheckRollbackSchemaFailureNotice());
        br.addItem("Advice");
        br.addElement("The AlterCheck requires that PreviousDDL are correct.");
        br.addElement("So you should prepare the PreviousDDL again.");
        final String msg = br.buildExceptionMessage();
        throw new DfAlterCheckRollbackSchemaFailureException(msg, e);
    }

    public String getAlterCheckRollbackSchemaFailureNotice() {
        return "Failed to rollback the schema as previous DB.";
    }

    // ===================================================================================
    //                                                                 PreviousDB Resource
    //                                                                 ===================
    // -----------------------------------------------------
    //                                         Previous Date
    //                                         -------------
    public String findLatestPreviousDate() {
        return doExtractPreviousDate(findLatestPreviousZip());
    }

    protected String doExtractPreviousDate(File previousZip) {
        if (previousZip == null) {
            return null;
        }
        final String previousName = Srl.substringLastFront(previousZip.getName(), ".");
        return Srl.substringFirstRear(previousName, "previous-");
    }

    // -----------------------------------------------------
    //                                      Extract Resource
    //                                      ----------------
    public boolean extractPreviousResource() {
        final File previousZip = findLatestPreviousZip();
        if (previousZip == null) {
            _log.info("*Not found the zip for previous resources");
            return false;
        }
        deleteExtractedPreviousResource();
        _log.info("...Extracting the previous resources from zip: " + resolvePath(previousZip));
        final DfZipArchiver archiver = new DfZipArchiver(previousZip);
        final Set<String> traceSet = new HashSet<String>();
        archiver.extract(new File(getMigrationPreviousDir()), file -> {
            final String path = resolvePath(file);
            traceSet.add(path);
            _log.info("  " + path);
            return true;
        });
        if (traceSet.isEmpty()) {
            String msg = "Not found the files in the zip: " + resolvePath(previousZip);
            throw new IllegalStateException(msg);
        }
        return true;
    }

    // -----------------------------------------------------
    //                                     Compress Resource
    //                                     -----------------
    public File findLatestPreviousZip() {
        final List<File> previousZipList = findPreviousZipList();
        if (previousZipList.isEmpty()) {
            return null;
        }
        return _alterControlAgent.findLatestNameFile(previousZipList);
    }

    public List<File> findPreviousZipList() {
        final File previousDir = new File(getMigrationPreviousDir());
        final File[] zipFiles = previousDir.listFiles(file -> isPreviousZip(file));
        final List<File> fileList;
        if (zipFiles != null) {
            fileList = Arrays.asList(zipFiles);
        } else {
            fileList = DfCollectionUtil.emptyList();
        }
        return fileList;
    }

    // -----------------------------------------------------
    //                                       Delete Resource
    //                                       ---------------
    public void deleteExtractedPreviousResource() {
        final List<File> previousFileList = _alterControlAgent.findHierarchyFileList(getMigrationPreviousDir());
        if (previousFileList.isEmpty()) {
            return;
        }
        _log.info("...Deleting the extracted previous resources");
        for (File previousFile : previousFileList) {
            if (isPreviousZip(previousFile)) {
                continue;
            }
            _alterControlAgent.deleteFile(previousFile, null);
        }
    }

    // -----------------------------------------------------
    //                                Previous Determination
    //                                ----------------------
    protected boolean isPreviousZip(File file) {
        final String name = file.getName();
        return name.startsWith("previous-") && name.endsWith(".zip");
    }

    // ===================================================================================
    //                                                                      General Helper
    //                                                                      ==============
    protected String resolvePath(File file) {
        return Srl.replace(file.getPath(), "\\", "/");
    }

    // ===================================================================================
    //                                                                          Properties
    //                                                                          ==========
    protected static DfBuildProperties getProperties() {
        return DfBuildProperties.getInstance();
    }

    protected static DfReplaceSchemaProperties getReplaceSchemaProperties() {
        return getProperties().getReplaceSchemaProperties();
    }

    protected String getMigrationPreviousDir() {
        return getReplaceSchemaProperties().getMigrationPreviousDir();
    }

    protected String getMigrationPreviousNGMark() {
        return getReplaceSchemaProperties().getMigrationPreviousNGMark();
    }
}
