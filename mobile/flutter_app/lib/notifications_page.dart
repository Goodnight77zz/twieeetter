import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'api_config.dart';
import 'app_lang.dart';
import 'tweet_detail_page.dart';
import 'api_helpers.dart';

class NotificationsPage extends StatefulWidget {
  const NotificationsPage({super.key});

  @override
  State<NotificationsPage> createState() => _NotificationsPageState();
}

class _NotificationsPageState extends State<NotificationsPage> {
  List<Map<String, dynamic>> allNotifications = [];
  bool isLoading = true;
  int currentUserId = 0;

  @override
  void initState() {
    super.initState();
    _loadNotifications();
  }

  Future<void> _loadNotifications() async {
    final prefs = await SharedPreferences.getInstance();
    currentUserId = prefs.getInt('userId') ?? 0;

    try {
      final res = await createSessionDio().get(
        '$baseUrl/notifications',
        queryParameters: {'userId': currentUserId},
      );
      if (!mounted) {
        return;
      }
      setState(() {
        allNotifications = asMapList(res.data);
        isLoading = false;
      });
      await context.read<LangProvider>().refreshUnreadCount();
    } catch (_) {
      if (!mounted) {
        return;
      }
      setState(() => isLoading = false);
    }
  }

  Future<void> _markAllAsRead() async {
    if (currentUserId == 0) {
      return;
    }
    try {
      await createSessionDio().post(
        '$baseUrl/notifications/mark-all-read',
        queryParameters: {'userId': currentUserId},
      );
      if (!mounted) {
        return;
      }
      setState(() {
        allNotifications = allNotifications.map((item) {
          final next = Map<String, dynamic>.from(item);
          next['read'] = true;
          next['isRead'] = true;
          return next;
        }).toList();
      });
      await context.read<LangProvider>().refreshUnreadCount();
    } catch (_) {}
  }

  Future<void> _markAsReadAndGo(Map<String, dynamic> notification) async {
    final langProvider = context.read<LangProvider>();
    final bool isRead =
        notification['isRead'] == true || notification['read'] == true;
    if (!isRead) {
      try {
        await createSessionDio().post(
          '$baseUrl/notifications/${notification['id']}/read',
        );
        await langProvider.refreshUnreadCount();
      } catch (_) {}
    }

    if (!mounted) {
      return;
    }

    final next = Map<String, dynamic>.from(notification);
    next['isRead'] = true;
    next['read'] = true;

    setState(() {
      allNotifications = allNotifications.map((item) {
        if (item['id'] == notification['id']) {
          return next;
        }
        return item;
      }).toList();
    });

    final tweetId = asInt(notification['tweetId']);
    if (tweetId > 0) {
      Navigator.push(
        context,
        MaterialPageRoute(builder: (_) => TweetDetailPage(tweetId: tweetId)),
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    final lang = context.watch<LangProvider>();

    return DefaultTabController(
      length: 3,
      child: Scaffold(
        appBar: AppBar(
          title: Text(lang.t('notifications_title')),
          backgroundColor: Colors.white,
          elevation: 0.5,
          actions: [
            TextButton(
              onPressed: _markAllAsRead,
              child: Text(lang.t('mark_all_read')),
            ),
          ],
          bottom: TabBar(
            labelColor: const Color(0xFFFB7299),
            unselectedLabelColor: Colors.grey,
            indicatorColor: const Color(0xFFFB7299),
            indicatorSize: TabBarIndicatorSize.label,
            tabs: [
              Tab(
                icon: const Icon(Icons.comment),
                text: lang.t('tab_notify_reply'),
              ),
              Tab(
                icon: const Icon(Icons.favorite),
                text: lang.t('tab_notify_like'),
              ),
              Tab(
                icon: const Icon(Icons.notifications_active_outlined),
                text: lang.t('tab_notify_fan'),
              ),
            ],
          ),
        ),
        body: isLoading
            ? const Center(child: CircularProgressIndicator())
            : TabBarView(
                children: [
                  _buildNotificationList(_replyTypes(), lang),
                  _buildNotificationList(_likeTypes(), lang),
                  _buildNotificationList(_otherTypes(), lang),
                ],
              ),
      ),
    );
  }

  Set<int> _replyTypes() => {1, 2, 5};

  Set<int> _likeTypes() => {0, 4};

  Set<int> _otherTypes() => {3};

  Widget _buildNotificationList(Set<int> types, LangProvider lang) {
    final filtered = allNotifications.where((item) {
      final type = asInt(item['type'], fallback: -1);
      return types.contains(type);
    }).toList();

    if (filtered.isEmpty) {
      return Center(
        child: Text(
          lang.t('no_notifications'),
          style: const TextStyle(color: Colors.grey),
        ),
      );
    }

    return RefreshIndicator(
      onRefresh: _loadNotifications,
      child: ListView.builder(
        itemCount: filtered.length,
        itemBuilder: (_, index) {
          final item = filtered[index];
          final actor = asMap(item['actor']);
          final bool isRead = item['isRead'] == true || item['read'] == true;

          return ListTile(
            tileColor: isRead
                ? null
                : const Color(0xFFE3F2FD).withValues(alpha: 0.5),
            contentPadding: const EdgeInsets.symmetric(
              horizontal: 14,
              vertical: 8,
            ),
            leading: CircleAvatar(
              backgroundImage: NetworkImage(buildAvatarUrl(actor)),
            ),
            title: Text.rich(
              TextSpan(
                children: [
                  TextSpan(
                    text: (actor['nickname'] ?? actor['username'] ?? 'User')
                        .toString(),
                    style: const TextStyle(
                      fontWeight: FontWeight.w700,
                      color: Color(0xFF111827),
                    ),
                  ),
                  const TextSpan(text: ' '),
                  TextSpan(
                    text: _messageForType(
                      asInt(item['type'], fallback: -1),
                      lang,
                    ),
                    style: const TextStyle(
                      color: Color(0xFF4B5563),
                      fontWeight: FontWeight.w500,
                    ),
                  ),
                ],
              ),
              maxLines: 2,
              overflow: TextOverflow.ellipsis,
              textScaler: const TextScaler.linear(1.0),
              style: const TextStyle(fontSize: 15, height: 1.35),
            ),
            subtitle: Text(
              item['createTime'].toString().split('T')[0],
              style: const TextStyle(fontSize: 13, color: Color(0xFF6B7280)),
              textScaler: const TextScaler.linear(1.0),
            ),
            onTap: () => _markAsReadAndGo(item),
          );
        },
      ),
    );
  }

  String _messageForType(int type, LangProvider lang) {
    switch (type) {
      case 0:
        return lang.t('liked_your_post');
      case 1:
        return lang.t('commented_your_post');
      case 2:
        return lang.t('replied_your_comment');
      case 3:
        return lang.t('subscription_update');
      case 4:
        return lang.t('liked_your_comment');
      case 5:
        return lang.t('accepted_your_comment');
      default:
        return lang.t('notifications_title');
    }
  }
}
