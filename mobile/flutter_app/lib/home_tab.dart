import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'api_config.dart';
import 'app_lang.dart';
import 'notifications_page.dart';
import 'search_page.dart';
import 'tweet_adapter.dart';
import 'tweet_detail_page.dart';
import 'api_helpers.dart';

class HomeTab extends StatefulWidget {
  const HomeTab({super.key});

  @override
  State<HomeTab> createState() => _HomeTabState();
}

class _HomeTabState extends State<HomeTab> {
  final Color mainBlue = const Color(0xFF00A1D6);

  List<Map<String, dynamic>> tweets = [];
  bool isLoading = true;
  String? myAvatarUrl;
  int? currentUserId;

  @override
  void initState() {
    super.initState();
    _initData();
  }

  Future<void> _initData() async {
    await _loadCurrentUser();
    await fetchData();
  }

  Future<void> _loadCurrentUser() async {
    final prefs = await SharedPreferences.getInstance();
    currentUserId = prefs.getInt('userId');
    if (currentUserId == null || currentUserId! <= 0) {
      return;
    }

    try {
      final res = await createSessionDio().get('$baseUrl/users/$currentUserId');
      if (!mounted) {
        return;
      }
      setState(() {
        myAvatarUrl = buildAvatarUrl(res.data);
      });
    } catch (_) {}
  }

  Future<void> fetchData() async {
    setState(() => isLoading = true);
    try {
      final res = await createSessionDio().get(
        '$baseUrl/tweets',
        queryParameters: {'sort': 'latest', 'limit': 50},
      );

      final rawList = asList(res.data);
      final processed = <Map<String, dynamic>>[];

      await Future.wait(
        rawList.map((item) async {
          final tweet = normalizeTweetItem(item);
          if (tweet == null) {
            return;
          }

          tweet['isLiked'] = false;
          tweet['isCommentExpanded'] = false;
          tweet['previewComments'] = <dynamic>[];

          if (currentUserId != null && currentUserId! > 0) {
            try {
              final likeRes = await createSessionDio().get(
                '$baseUrl/tweets/${tweet['id']}/like-status',
                queryParameters: {'userId': currentUserId},
              );
              tweet['isLiked'] = likeRes.data['isLiked'] == true;
              tweet['likeCount'] = likeRes.data['count'] ?? tweet['likeCount'];
            } catch (_) {}
          }

          processed.add(tweet);
        }),
      );

      processed.sort((a, b) {
        final left = (a['updateTime'] ?? a['createTime'] ?? '').toString();
        final right = (b['updateTime'] ?? b['createTime'] ?? '').toString();
        return right.compareTo(left);
      });

      if (!mounted) {
        return;
      }
      setState(() {
        tweets = processed;
        isLoading = false;
      });
    } catch (_) {
      if (!mounted) {
        return;
      }
      setState(() => isLoading = false);
    }
  }

  Future<void> _toggleLike(int index) async {
    if (currentUserId == null || currentUserId! <= 0) {
      return;
    }

    final tweet = tweets[index];
    final bool oldState = tweet['isLiked'] == true;
    final int oldCount = tweet['likeCount'] ?? 0;

    setState(() {
      tweets[index]['isLiked'] = !oldState;
      tweets[index]['likeCount'] = oldState ? oldCount - 1 : oldCount + 1;
    });

    try {
      final res = await createSessionDio().post(
        '$baseUrl/tweets/${tweet['id']}/like',
        queryParameters: {'userId': currentUserId},
      );
      if (!mounted) {
        return;
      }
      setState(() {
        tweets[index]['isLiked'] = res.data['isLiked'] == true;
        tweets[index]['likeCount'] =
            res.data['count'] ?? tweets[index]['likeCount'];
      });
    } catch (_) {
      if (!mounted) {
        return;
      }
      setState(() {
        tweets[index]['isLiked'] = oldState;
        tweets[index]['likeCount'] = oldCount;
      });
    }
  }

  Future<void> _recordShare(int index) async {
    final tweet = tweets[index];
    try {
      final res = await createSessionDio().post(
        '$baseUrl/tweets/${tweet['id']}/share',
      );
      if (!mounted) {
        return;
      }
      setState(() {
        tweets[index]['shareCount'] = res.data['shares'] ?? tweet['shareCount'];
        final signals = Map<String, dynamic>.from(res.data['signals'] ?? {});
        if (signals.isNotEmpty) {
          tweets[index]['hotScore'] = signals['hotScore'] ?? tweet['hotScore'];
        }
      });
    } catch (_) {}
  }

  Future<void> _toggleCommentDropdown(int index) async {
    final tweet = tweets[index];

    if (tweet['isCommentExpanded'] == true) {
      setState(() {
        tweets[index]['isCommentExpanded'] = false;
      });
      return;
    }

    setState(() {
      tweets[index]['isCommentExpanded'] = true;
    });

    try {
      final res = await createSessionDio().get(
        '$baseUrl/tweets/${tweet['id']}/comments',
        queryParameters: {'userId': currentUserId},
      );
      if (!mounted) {
        return;
      }
      setState(() {
        tweets[index]['previewComments'] = asList(res.data).take(5).toList();
      });
    } catch (_) {}
  }

  @override
  Widget build(BuildContext context) {
    final lang = context.watch<LangProvider>();

    return Scaffold(
      appBar: AppBar(
        elevation: 0,
        surfaceTintColor: Colors.white,
        leading: Padding(
          padding: const EdgeInsets.all(10),
          child: CircleAvatar(
            backgroundImage: NetworkImage(myAvatarUrl ?? defaultAvatarUrl),
          ),
        ),
        title: InkWell(
          onTap: () {
            Navigator.push(
              context,
              MaterialPageRoute(builder: (_) => const SearchPage()),
            );
          },
          child: Container(
            height: 38,
            decoration: BoxDecoration(
              color: const Color(0xFFF1F2F4),
              borderRadius: BorderRadius.circular(20),
            ),
            child: Row(
              children: [
                const SizedBox(width: 10),
                const Icon(Icons.search, size: 18, color: Colors.grey),
                const SizedBox(width: 8),
                Text(
                  lang.t('search_hint'),
                  style: const TextStyle(color: Colors.grey, fontSize: 14),
                ),
              ],
            ),
          ),
        ),
        actions: [
          Stack(
            children: [
              IconButton(
                icon: const Icon(Icons.mail_outline, color: Colors.grey),
                onPressed: () {
                  Navigator.push(
                    context,
                    MaterialPageRoute(
                      builder: (_) => const NotificationsPage(),
                    ),
                  );
                },
              ),
              Consumer<LangProvider>(
                builder: (_, provider, __) {
                  if (provider.unreadCount <= 0) {
                    return const SizedBox.shrink();
                  }
                  return Positioned(
                    top: 8,
                    right: 8,
                    child: Container(
                      padding: const EdgeInsets.all(2),
                      decoration: BoxDecoration(
                        color: Colors.red,
                        borderRadius: BorderRadius.circular(6),
                      ),
                      constraints: const BoxConstraints(
                        minWidth: 12,
                        minHeight: 12,
                      ),
                      child: Text(
                        '${provider.unreadCount}',
                        style: const TextStyle(
                          color: Colors.white,
                          fontSize: 8,
                        ),
                        textAlign: TextAlign.center,
                      ),
                    ),
                  );
                },
              ),
            ],
          ),
        ],
      ),
      body: isLoading
          ? Center(child: CircularProgressIndicator(color: mainBlue))
          : RefreshIndicator(
              onRefresh: fetchData,
              color: mainBlue,
              child: ListView.builder(
                itemCount: tweets.length,
                itemBuilder: (_, index) => _buildCard(index, lang),
              ),
            ),
    );
  }

  Widget _buildCard(int index, LangProvider lang) {
    final tweet = tweets[index];
    final author = Map<String, dynamic>.from(tweet['author'] ?? {});
    final String timeStr = (tweet['createTime'] ?? '').toString().split('T')[0];
    final String fileName = (tweet['originalFilename'] ?? '').toString();
    final String tags = (tweet['tags'] ?? '').toString();
    final String title = (tweet['title'] ?? '').toString().trim();
    final List previewComments = List<dynamic>.from(
      tweet['previewComments'] ?? [],
    );

    return InkWell(
      onTap: () {
        Navigator.push(
          context,
          MaterialPageRoute(
            builder: (_) => TweetDetailPage(tweetId: tweet['id']),
          ),
        );
      },
      child: Container(
        margin: const EdgeInsets.only(bottom: 8),
        color: Colors.white,
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                CircleAvatar(
                  radius: 20,
                  backgroundColor: Colors.grey.shade200,
                  backgroundImage: NetworkImage(buildAvatarUrl(author)),
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        (author['nickname'] ?? author['username'] ?? 'Scholar')
                            .toString(),
                        style: const TextStyle(
                          fontWeight: FontWeight.bold,
                          fontSize: 15,
                          color: Colors.black87,
                        ),
                      ),
                      Text(
                        timeStr,
                        style: const TextStyle(
                          color: Colors.grey,
                          fontSize: 11,
                        ),
                      ),
                    ],
                  ),
                ),
              ],
            ),
            const SizedBox(height: 10),
            if (title.isNotEmpty)
              Padding(
                padding: const EdgeInsets.only(bottom: 8),
                child: Text(
                  title,
                  style: const TextStyle(
                    fontSize: 18,
                    fontWeight: FontWeight.bold,
                    color: Colors.black87,
                  ),
                ),
              ),
            Text(
              (tweet['content'] ?? '').toString(),
              style: const TextStyle(
                fontSize: 15,
                height: 1.5,
                color: Colors.black87,
              ),
            ),
            const SizedBox(height: 10),
            if (tags.isNotEmpty)
              Padding(
                padding: const EdgeInsets.only(bottom: 10),
                child: Wrap(
                  spacing: 8,
                  runSpacing: 8,
                  children: tags
                      .split(',')
                      .map((tag) => tag.trim())
                      .where((tag) => tag.isNotEmpty)
                      .map(
                        (tag) => Container(
                          padding: const EdgeInsets.symmetric(
                            horizontal: 8,
                            vertical: 2,
                          ),
                          decoration: BoxDecoration(
                            color: const Color(0xFFE8F5FE),
                            borderRadius: BorderRadius.circular(4),
                          ),
                          child: Text(
                            '#${lang.tTag(tag)}',
                            style: TextStyle(color: mainBlue, fontSize: 12),
                          ),
                        ),
                      )
                      .toList(),
                ),
              ),
            if (fileName.isNotEmpty)
              Container(
                padding: const EdgeInsets.all(12),
                decoration: BoxDecoration(
                  color: const Color(0xFFF7F9FA),
                  borderRadius: BorderRadius.circular(8),
                  border: Border.all(color: const Color(0xFFEFF3F4)),
                ),
                child: Row(
                  children: [
                    Icon(Icons.description, color: mainBlue, size: 28),
                    const SizedBox(width: 10),
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(
                            lang.t('attachment'),
                            style: const TextStyle(
                              fontSize: 11,
                              color: Colors.grey,
                            ),
                          ),
                          const SizedBox(height: 2),
                          Text(
                            fileName,
                            style: TextStyle(
                              fontWeight: FontWeight.w500,
                              fontSize: 13,
                              color: mainBlue,
                            ),
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                          ),
                        ],
                      ),
                    ),
                  ],
                ),
              ),
            const SizedBox(height: 12),
            Wrap(
              spacing: 8,
              runSpacing: 8,
              children: [
                _buildSignalChip(
                  Icons.star,
                  (tweet['avgScore'] ?? 0).toString(),
                ),
                _buildSignalChip(
                  Icons.local_fire_department,
                  '${(tweet['hotScore'] ?? 0).round()}',
                ),
                _buildSignalChip(
                  Icons.download_outlined,
                  '${tweet['downloadCount'] ?? 0}',
                ),
              ],
            ),
            const SizedBox(height: 12),
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                _buildActionBtn(
                  Icons.share_outlined,
                  lang.t('share'),
                  Colors.grey,
                  () => _recordShare(index),
                ),
                _buildActionBtn(
                  tweet['isCommentExpanded'] == true
                      ? Icons.chat_bubble
                      : Icons.chat_bubble_outline,
                  (tweet['commentCount'] ?? 0) > 0
                      ? '${tweet['commentCount']}'
                      : lang.t('comment'),
                  tweet['isCommentExpanded'] == true ? mainBlue : Colors.grey,
                  () => _toggleCommentDropdown(index),
                ),
                _buildActionBtn(
                  tweet['isLiked'] == true
                      ? Icons.thumb_up
                      : Icons.thumb_up_off_alt,
                  (tweet['likeCount'] ?? 0) > 0
                      ? '${tweet['likeCount']}'
                      : 'Like',
                  tweet['isLiked'] == true
                      ? const Color(0xFFF91880)
                      : Colors.grey,
                  () => _toggleLike(index),
                ),
              ],
            ),
            AnimatedSize(
              duration: const Duration(milliseconds: 300),
              curve: Curves.easeInOut,
              child: tweet['isCommentExpanded'] == true
                  ? Container(
                      width: double.infinity,
                      margin: const EdgeInsets.only(top: 12),
                      padding: const EdgeInsets.all(12),
                      decoration: BoxDecoration(
                        color: const Color(0xFFF7F8FA),
                        borderRadius: BorderRadius.circular(12),
                      ),
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          if (previewComments.isEmpty)
                            const Padding(
                              padding: EdgeInsets.all(8),
                              child: Text(
                                'No comments yet / Loading...',
                                style: TextStyle(
                                  color: Colors.grey,
                                  fontSize: 12,
                                ),
                              ),
                            )
                          else
                            ...previewComments.map(_buildPrettyCommentRow),
                          if (previewComments.isNotEmpty)
                            Padding(
                              padding: const EdgeInsets.only(top: 8, left: 40),
                              child: InkWell(
                                onTap: () {
                                  Navigator.push(
                                    context,
                                    MaterialPageRoute(
                                      builder: (_) =>
                                          TweetDetailPage(tweetId: tweet['id']),
                                    ),
                                  );
                                },
                                child: Text(
                                  'View more comments >',
                                  style: TextStyle(
                                    color: mainBlue,
                                    fontSize: 12,
                                    fontWeight: FontWeight.bold,
                                  ),
                                ),
                              ),
                            ),
                        ],
                      ),
                    )
                  : const SizedBox.shrink(),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildPrettyCommentRow(dynamic item) {
    final comment = asMap(item);
    final user = Map<String, dynamic>.from(comment['user'] ?? {});

    return Padding(
      padding: const EdgeInsets.only(bottom: 12),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          CircleAvatar(
            radius: 12,
            backgroundColor: Colors.grey.shade200,
            backgroundImage: NetworkImage(buildAvatarUrl(user)),
          ),
          const SizedBox(width: 8),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  (user['nickname'] ?? user['username'] ?? 'User').toString(),
                  style: TextStyle(
                    color: mainBlue.withOpacity(0.8),
                    fontSize: 12,
                    fontWeight: FontWeight.bold,
                  ),
                ),
                const SizedBox(height: 2),
                Text(
                  (comment['content'] ?? '').toString(),
                  style: const TextStyle(
                    color: Colors.black87,
                    fontSize: 13,
                    height: 1.4,
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildActionBtn(
    IconData icon,
    String label,
    Color color,
    VoidCallback onTap,
  ) {
    return InkWell(
      onTap: onTap,
      child: Padding(
        padding: const EdgeInsets.all(8),
        child: Row(
          children: [
            Icon(icon, size: 18, color: color),
            const SizedBox(width: 4),
            Text(label, style: TextStyle(color: color, fontSize: 12)),
          ],
        ),
      ),
    );
  }

  Widget _buildSignalChip(IconData icon, String value) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
      decoration: BoxDecoration(
        color: const Color(0xFFF7F9FA),
        borderRadius: BorderRadius.circular(20),
        border: Border.all(color: const Color(0xFFE5E7EB)),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(icon, size: 14, color: Colors.grey.shade700),
          const SizedBox(width: 6),
          Text(
            value,
            style: const TextStyle(fontSize: 12, fontWeight: FontWeight.w600),
          ),
        ],
      ),
    );
  }
}
