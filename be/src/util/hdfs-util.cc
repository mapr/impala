// Licensed to the Apache Software Foundation (ASF) under one
// or more contributor license agreements.  See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership.  The ASF licenses this file
// to you under the Apache License, Version 2.0 (the
// "License"); you may not use this file except in compliance
// with the License.  You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.

#include "util/hdfs-util.h"

#include <sstream>
#include <string.h>
#include <iostream>
#include <fstream>
#include <stdio.h>
#include <stdlib.h>

#include "util/error-util.h"

#include "common/names.h"
#include "runtime/exec-env.h"

namespace impala {

string GetHdfsErrorMsg(const string& prefix, const string& file) {
  string error_msg = GetStrErrMsg();
  stringstream ss;
  ss << prefix << file << "\n" << error_msg;
  return ss.str();
}

Status GetFileSize(const hdfsFS& connection, const char* filename, int64_t* filesize) {
  hdfsFileInfo* info = hdfsGetPathInfo(connection, filename);
  if (info == NULL) return Status(GetHdfsErrorMsg("Failed to get file info ", filename));
  *filesize = info->mSize;
  hdfsFreeFileInfo(info, 1);
  return Status::OK();
}

Status GetLastModificationTime(const hdfsFS& connection, const char* filename,
                               time_t* last_mod_time) {
  hdfsFileInfo* info = hdfsGetPathInfo(connection, filename);
  if (info == NULL) return Status(GetHdfsErrorMsg("Failed to get file info ", filename));
  *last_mod_time = info->mLastMod;
  hdfsFreeFileInfo(info, 1);
  return Status::OK();
}

bool IsHiddenFile(const string& filename) {
  return !filename.empty() && (filename[0] == '.' || filename[0] == '_');
}

Status hdfsCopyImpl(const hdfsFS& src_conn, const string& src_path,
                  const hdfsFS& dst_conn, const string& dst_path) {
  tSize readBytes = 0;
  tSize readLength = 8 * 1024;

  // create a file
  FILE * pFile;
  hdfsFile srcFile = hdfsOpenFile(src_conn, src_path.c_str(), O_RDONLY, 0, 0, 0);

  // allocated buffer of size 8k
  void *buffer = malloc (readLength);

  readBytes = hdfsRead(src_conn, srcFile, buffer, readLength);

  // prevent creating empty files when read fails or readed zero bytes

  bool readNotZero = (readBytes > 0);

  if (readNotZero) {
    pFile = fopen(dst_path.c_str(), "wb");

      if (pFile == NULL) {
        stringstream ss;
        ss << "Failed to create or open local file:" << dst_path;
        return Status(ss.str());
      }
  }

  while (readBytes != 0) {
    if (readBytes == -1) {
      stringstream ss;
      ss << "Failed to read from path:" << src_path;
      return Status(ss.str());
    }
    fwrite(buffer, sizeof(char), readBytes, pFile);

    // Check if the write was successful
    if (ferror(pFile)) {
      stringstream ss;
      ss << "Failed to write to path:" << dst_path;
      return Status(ss.str());
    }
    readBytes = hdfsRead(src_conn, srcFile, buffer, readLength);
  }
  if (readNotZero) {
    fclose(pFile);
  }

  hdfsCloseFile(src_conn, srcFile);

  free(buffer);

  // MAPR-22240 for JAVA filesystem client try to use hdfsCopy
  if (!readNotZero) {
    int error = hdfsCopy(src_conn, REMOVELEADINGSLASHES(PATHONLY(src_path.c_str())), dst_conn, dst_path.c_str());
    if (error != 0) {
      string error_msg = GetHdfsErrorMsg("");
      stringstream ss;
      ss << "Failed to copy " << src_path << " to " << dst_path << ": " << error_msg;
      return Status(ss.str());
    }
  }
  return Status::OK();
}

Status CopyHdfsFile(const hdfsFS& src_conn, const string& src_path,
                    const hdfsFS& dst_conn, const string& dst_path) {
  return hdfsCopyImpl(src_conn, src_path, dst_conn, dst_path);
}

bool IsHdfsPath(const char* path) {
  if (strstr(path, ":/") == NULL) {
    return ExecEnv::GetInstance()->default_fs().compare(0, 7, "hdfs://") == 0;
  }
  return strncmp(path, "hdfs://", 7) == 0;
}

bool IsS3APath(const char* path) {
  if (strstr(path, ":/") == NULL) {
    return ExecEnv::GetInstance()->default_fs().compare(0, 6, "s3a://") == 0;
  }
  return strncmp(path, "s3a://", 6) == 0;
}

// Returns the length of the filesystem name in 'path' which is the length of the
// 'scheme://authority'. Returns 0 if the path is unqualified.
static int GetFilesystemNameLength(const char* path) {
  // Special case for "file:/". It will not have an authority following it.
  if (strncmp(path, "file:", 5) == 0) return 5;

  const char* after_scheme = strstr(path, "://");
  if (after_scheme == NULL) return 0;
  // Some paths may come only with a scheme. We add 3 to skip over "://".
  if (*(after_scheme + 3) == '\0') return strlen(path);

  const char* after_authority = strstr(after_scheme + 3, "/");
  if (after_authority == NULL) return strlen(path);
  return after_authority - path;
}

bool FilesystemsMatch(const char* path_a, const char* path_b) {
  int fs_a_name_length = GetFilesystemNameLength(path_a);
  int fs_b_name_length = GetFilesystemNameLength(path_b);

  const char* default_fs = ExecEnv::GetInstance()->default_fs().c_str();
  int default_fs_name_length = GetFilesystemNameLength(default_fs);

  // Neither is fully qualified: both are on default_fs.
  if (fs_a_name_length == 0 && fs_b_name_length == 0) return true;
  // One is a relative path: check fully-qualified one against default_fs.
  if (fs_a_name_length == 0) {
    DCHECK_GT(fs_b_name_length, 0);
    return strncmp(path_b, default_fs, default_fs_name_length) == 0;
  }
  if (fs_b_name_length == 0) {
    DCHECK_GT(fs_a_name_length, 0);
    return strncmp(path_a, default_fs, default_fs_name_length) == 0;
  }
  DCHECK_GT(fs_a_name_length, 0);
  DCHECK_GT(fs_b_name_length, 0);
  // Both fully qualified: check the filesystem prefix.
  if (fs_a_name_length != fs_b_name_length) return false;
  return strncmp(path_a, path_b, fs_a_name_length) == 0;
}

}
