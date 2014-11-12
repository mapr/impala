// Copyright 2012 Cloudera Inc.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

#include "util/hdfs-util.h"

#include <sstream>
#include <string.h>
#include <iostream>
#include <fstream>
#include <stdio.h>
#include <stdlib.h>
#include "util/error-util.h"

using namespace boost;
using namespace std;

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
  return Status::OK;
}

Status GetLastModificationTime(const hdfsFS& connection, const char* filename,
                               time_t* last_mod_time) {
  hdfsFileInfo* info = hdfsGetPathInfo(connection, filename);
  if (info == NULL) return Status(GetHdfsErrorMsg("Failed to get file info ", filename));
  *last_mod_time = info->mLastMod;
  hdfsFreeFileInfo(info, 1);
  return Status::OK;
}

bool IsHiddenFile(const string& filename) {
  return !filename.empty() && (filename[0] == '.' || filename[0] == '_');
}

Status hdfsCopyImpl(const hdfsFS& src_conn, const string& src_path,
                  const hdfsFS& dst_conn, const string& dst_path) {
  tSize readStatus = 0;
  tSize readLength = 8 * 1024;

  //create a file 
  FILE * pFile;
  pFile = fopen(dst_path.c_str(), "wb");

  if (pFile == NULL) {
    stringstream ss;
    ss << "Failed to create or open local file:" << dst_path;
    return Status(ss.str());    
  }
  hdfsFile srcFile = hdfsOpenFile(src_conn, src_path.c_str(), O_RDONLY, 0, 0, 0);

  // allocated buffer of size 8k
  void *buffer = malloc (readLength);

  readStatus = hdfsRead(src_conn, srcFile, buffer, readLength);
  while (readStatus != 0) {
    if (readStatus == -1) {
      stringstream ss;
      ss << "Failed to read from path:" << src_path;
      return Status(ss.str());
    }
    fwrite(buffer, sizeof(char), readStatus, pFile);
 
    // Check if the write was successful
    if (ferror(pFile)) {
      stringstream ss;
      ss << "Failed to write to path:" << dst_path;
      return Status(ss.str());      
    }
    readStatus = hdfsRead(src_conn, srcFile, buffer, readLength);
  } 

  fclose(pFile);

  hdfsCloseFile(src_conn, srcFile);

  free(buffer);

  return Status::OK;
}

Status CopyHdfsFile(const hdfsFS& src_conn, const string& src_path,
                    const hdfsFS& dst_conn, const string& dst_path) {
  return hdfsCopyImpl(src_conn, src_path, dst_conn, dst_path);
}

}
