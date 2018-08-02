/************************************************************************
  e2immutable is a tiny 'exported' command line 'library' for reading
  'Immutable/Append' and changing 'Immutable' attribute

  Copyright (C) 2018 duangsuse

  Licensed under the Apache License, Version 2.0 (the "License");
  you may not use this file except in compliance with the License.
  You may obtain a copy of the License at

      http://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License.
************************************************************************/

/** @file e2immutable.c
 *
 * @brief A __command-line library__ for _manipulating_ Ext2 family filesystem __file flags__
 *
 * E2immutable is a tiny __exported__ command line _library_ for reading __Immutable/Append__ and changing __Immutable__ attribute
 *
 * It's loosely licensed under __Apache 2.0__, developed for an _Android cleaning software_ called __Dir__
 */


#include <unistd.h>
#include <stdlib.h>

#include <errno.h>

#include <stdio.h>
#include <fcntl.h>

#include <sys/ioctl.h>
#include <sys/stat.h>
#include <string.h>

#define ATTR_I 0x00000010 /**< Ext2fs __Immutable__ file attribute. */
#define ATTR_A 0x00000020 /**< Ext2fs __Append Only__ file attribute. */

#define EXT2_IOC_GETFLAGS _IOR('f', 1, long) /**< Ext2 filesystem `getflags` ioctl call. */
#define EXT2_IOC_SETFLAGS _IOW('f', 2, long) /**< Ext2 filesystem `setflags` ioctl call. */

#if __WORDSIZE == 64
#define E2IMM_IS_64BIT /**< Will be defined if using __64-bit__ compile target. */
#endif

/** @def addattr
 *
 * @brief add _fattr_ a flag
 * @param fattr Bitwise flags number
 * @param flag Bitwise _flag_ to add
 *
 * Adds bytes __fattr__ a bitwise __flag__
 */
#define addattr(fattr, flag) (fattr) = (fattr) | (flag)

/** @def delattr
 *
 * @brief remove _fattr_ a flag
 * @param fattr Bitwise flags number
 * @param flag Bitwise _flag_ to remove
 *
 * Removes bytes __fattr__ a bitwise __flag__
 */
#define delattr(fattr, flag) (fattr) = (fattr) & ~(flag)

typedef unsigned long fsattrs_t; /**< Ext2 filesystem __file flags__ type */

/// Close file descriptor, __ignore errors__
/**
 * @param fd _File descriptor_ to `close`
 */
static inline void close_silently(int fd) {
    int saved_errno = errno;
    close(fd); // Operate
    errno = saved_errno;
}

/// Open a __file descriptor__ for __reading or changing file attribute__
/**
 * @param path File path, must be a __regular file__ or a __folder__
 * @return
 * + __-1__ if `open` fails
 * + __-2__ if `stat` fails
 * + __-3__ if file __not acceptable__
 */
int open_attrsctl_fd(const char *path) {
    struct stat fstate;

    if (stat(path, &fstate) != 0)
        return -2; // stat fails

    if (!S_ISREG(fstate.st_mode) && !S_ISDIR(fstate.st_mode))
        return -3; // not acceptable

    return open(path, O_RDONLY | O_NONBLOCK);
}

/// Get file attribute
/**
 * @param path File path to get
 * @param buffer Result buffer to use, a pointer to `unsigned long`
 * @see fsattrs_t use as parameter _buffer_'s type
 * @see open_attrsctl_fd This function uses this to open fd
 * @return
 * + __-1__ if getting attributes failed, maybe sets `errno`
 * + __-2__ if `stat` or `open` fails
 */
int fgetattr(const char *path, fsattrs_t *buffer) {
    int fd, ret;
#ifdef E2IMM_IS_64BIT
    int flags; // 64bit
#endif

    fd = open_attrsctl_fd(path);
    if (fd == -2)
        return -2;
    if (fd == -1)
        return -1;

    if (fd == -3) {
        errno = EOPNOTSUPP;
        return -1;
    }

#ifdef E2IMM_IS_64BIT
    ret = ioctl(fd, EXT2_IOC_GETFLAGS, &flags);
    *buffer = (unsigned long) flags;
#else
    ret = ioctl(fd, EXT2_IOC_GETFLAGS, (void *) buffer);
#endif

    close_silently(fd);
    return ret;
}

/// Set file attribute
/**
 * @param path File path to set
 * @param buffer Attributes buffer to use, an `unsigned long`
 * @see fsattrs_t use as parameter _buffer_'s type
 * @see open_attrsctl_fd This function uses this to open fd
 * @return
 * + __-1__ if setting attributes failed, maybe sets `errno`
 * + __-2__ if `stat` or `open` fails
 */
int fsetattr(const char *path, fsattrs_t buffer) {
    int fd, ret;
#ifdef E2IMM_IS_64BIT
    int flags; // 64bit
#endif

    fd = open_attrsctl_fd(path);
    if (fd == -2)
        return -2;
    if (fd == -1)
        return -1;

    if (fd == -3) {
        errno = EOPNOTSUPP;
        return -1;
    }

#ifdef E2IMM_IS_64BIT
    flags = (int) buffer;
    ret = ioctl(fd, EXT2_IOC_SETFLAGS, &flags);
#else
    ret = ioctl(fd, EXT2_IOC_SETFLAGS, (void *) &buffer);
#endif

    close_silently(fd);
    return ret;
}

/// Print error string
/**
 * Prints `strerror(errno)` to `stderr`
 */
inline void print_error() {
    fprintf(stderr, "%s\n", strerror(errno));
}


/// Utility for changing file __Immutable__ attribute and reading file __Append and Immutable__ attribute.

/// + Author: __duangsuse__
/// + Language: __C99 / C++ 11__
/// + Created: __Jul, 2018__
/// + License: __Apache License 2.0__
///
/// Usage: e2immutable (+/-/@) <file path>
/// --------
/// + @: __query file attr__
///   + return __0__: no attribute
///   + return __255__: `stat`/`open`/cmdline fails
///   + return __254__: `fgetfattr` fails
///   + return __1__: +i
///   + return __2__: +a
///   + return __3__: +i +a
/// + +: __add immutable attribute__
///   + return __0__: OK, changed
///   + return __1__: OK, unchanged
///   + return __255__: `stat`/`open`/cmdline fails
///   + return __254__: `fsetfattr` or `fgetattr` fails
/// + -: __remove immutable attribute__
///   + return __0__: OK, changed
///   + return __1__: OK, unchanged
///   + return __255__: `stat`/`open`/cmdline fails
///   + return __254__: `fsetfattr` or `fgetattr` fails
int main(int argc, char *argv[]) {
    if (argc != 3)
        exit(-1);

    // is argv[1] single character?
    if (argv[1][1] != '\00')
        exit(-1);

    char *path = argv[2];

    int ret = 0;
    fsattrs_t fs;

    switch (fgetattr(path, &fs)) {
        case -1:
            print_error();
            return -2;

        case -2:
            return -1;

        default:
            break;
    }

    switch (argv[1][0]) {
        case '@':
            if (fs & ATTR_A)
                ret += 2;
            if (fs & ATTR_I)
                ret += 1;
            return ret;

        case '+':
            if (fs & ATTR_I)
                return 1;
            addattr(fs, ATTR_I);
            break;

        case '-':
            if (!(fs & ATTR_I))
                return 1;
            delattr(fs, ATTR_I);
            break;

        default:
            return -1;
    }

    switch (fsetattr(path, fs)) {
        case -1:
            print_error();
            return -2;

        case -2:
            return -1;

        default:
            break;
    }

    return 0;
}
