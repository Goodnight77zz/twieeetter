import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'api_config.dart';
import 'app_lang.dart';
import 'edit_profile_page.dart';
import 'main_screen.dart';
import 'my_content_page.dart';
import 'tweet_detail_page.dart';
import 'api_helpers.dart';

class ProfilePage extends StatefulWidget {
  const ProfilePage({super.key});

  @override
  State<ProfilePage> createState() => _ProfilePageState();
}

class _ProfilePageState extends State<ProfilePage> {
  final Color mainBlue = const Color(0xFF00A1D6);
  final Dio _dio = createSessionDio();

  Map<String, dynamic>? userProfile;
  Map<String, dynamic>? userStats;
  Map<String, dynamic>? archiveStats;
  Map<String, dynamic>? interestProfile;
  List<Map<String, dynamic>> subscriptions = [];
  List<Map<String, dynamic>> favorites = [];

  bool isLoading = true;
  bool isRefreshing = false;
  bool isRemovingSubscription = false;
  int currentUserId = 0;

  @override
  void initState() {
    super.initState();
    _loadProfileData();
  }

  Future<void> _loadProfileData({bool silent = false}) async {
    final prefs = await SharedPreferences.getInstance();
    final userId = prefs.getInt('userId') ?? 0;

    if (!silent && mounted) {
      setState(() => isLoading = true);
    } else if (silent && mounted) {
      setState(() => isRefreshing = true);
    }

    if (userId == 0) {
      if (!mounted) return;
      setState(() {
        currentUserId = 0;
        isLoading = false;
        isRefreshing = false;
      });
      return;
    }

    try {
      final results = await Future.wait([
        _dio.get('$baseUrl/users/$userId'),
        _dio.get('$baseUrl/users/$userId/stats'),
        _dio.get('$baseUrl/users/$userId/research-archive'),
        _dio.get('$baseUrl/users/$userId/interest-profile'),
        _dio.get(
          '$baseUrl/subscriptions/my',
          queryParameters: {'userId': userId},
        ),
        _dio.get(
          '$baseUrl/tweets/favorites',
          queryParameters: {'userId': userId},
        ),
      ]);

      if (!mounted) return;
      setState(() {
        currentUserId = userId;
        userProfile = asMap(results[0].data);
        userStats = asMap(results[1].data);
        archiveStats = asMap(results[2].data);
        interestProfile = asMap(results[3].data);
        subscriptions = asMapList(results[4].data);
        favorites = asMapList(results[5].data);
        isLoading = false;
        isRefreshing = false;
      });
    } catch (_) {
      if (!mounted) return;
      setState(() {
        isLoading = false;
        isRefreshing = false;
      });
    }
  }

  Future<void> _logout() async {
    try {
      await _dio.post('$baseUrl/auth/logout');
    } catch (_) {}
    await clearSessionState();
    if (!mounted) return;
    Navigator.pushNamedAndRemoveUntil(context, '/login', (route) => false);
  }

  Future<void> _removeSubscription(Map<String, dynamic> item) async {
    if (currentUserId == 0 || isRemovingSubscription) return;
    final id = asInt(item['id'], fallback: -1);
    final area = _displayText(
      item['researchArea'] ?? item['targetValue'],
      fallback: '-',
    );
    if (id <= 0) return;

    setState(() => isRemovingSubscription = true);
    try {
      await _dio.delete(
        '$baseUrl/subscriptions/$id',
        queryParameters: {'userId': currentUserId},
      );
      if (!mounted) return;
      setState(() {
        subscriptions.removeWhere(
          (sub) => asInt(sub['id'], fallback: -1) == id,
        );
        isRemovingSubscription = false;
      });
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(_text('已取消订阅 $area', 'Unsubscribed from $area')),
        ),
      );
    } catch (_) {
      if (!mounted) return;
      setState(() => isRemovingSubscription = false);
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(
            _text(
              '取消订阅失败，请稍后重试',
              'Failed to unsubscribe. Please try again later.',
            ),
          ),
        ),
      );
    }
  }

  String _text(String zh, String en) {
    final lang = context.read<LangProvider>();
    return lang.currentLang == 'zh' ? zh : en;
  }

  String _displayText(dynamic value, {String fallback = '-'}) {
    final text = value?.toString().trim();
    if (text == null || text.isEmpty || text.toLowerCase() == 'null') {
      return fallback;
    }
    return text;
  }

  String _formatDate(dynamic value) {
    final text = value?.toString() ?? '';
    if (text.isEmpty) return '-';
    return text.replaceFirst('T', ' ').split('.').first;
  }

  int _readInt(dynamic value) {
    if (value is int) return value;
    if (value is double) return value.round();
    return int.tryParse(value?.toString() ?? '') ?? 0;
  }

  double _readDouble(dynamic value) {
    if (value is double) return value;
    if (value is int) return value.toDouble();
    return double.tryParse(value?.toString() ?? '') ?? 0;
  }

  String _getTitleKey(int score) {
    if (score < 100) return 'title_student';
    if (score < 500) return 'title_lecturer';
    return 'title_professor';
  }

  Color _getTitleColor(int score) {
    if (score < 100) return Colors.grey;
    if (score < 500) return mainBlue;
    return const Color(0xFFFFC107);
  }

  void _openPublish() {
    final mainState = context.findAncestorStateOfType<MainScreenState>();
    mainState?.switchToPublish();
  }

  @override
  Widget build(BuildContext context) {
    final lang = context.watch<LangProvider>();

    if (isLoading) {
      return Scaffold(
        backgroundColor: const Color(0xFFF8FAFC),
        appBar: AppBar(
          elevation: 0,
          backgroundColor: Colors.white,
          title: Text(
            lang.t('tab_mine'),
            style: const TextStyle(color: Colors.black),
          ),
          centerTitle: true,
        ),
        body: Center(child: CircularProgressIndicator(color: mainBlue)),
      );
    }

    final nickname = _displayText(
      userProfile?['nickname'] ?? userProfile?['username'],
      fallback: 'User',
    );
    final username = _displayText(userProfile?['username'], fallback: 'user');
    final bio = _displayText(
      userProfile?['bio'],
      fallback: _text(
        '这个研究者还没有填写简介。',
        'This researcher has not added a bio yet.',
      ),
    );
    final reputation = _readInt(userProfile?['reputation']);
    final title = lang.t(_getTitleKey(reputation));
    final titleColor = _getTitleColor(reputation);

    return Scaffold(
      backgroundColor: const Color(0xFFF8FAFC),
      appBar: AppBar(
        elevation: 0,
        backgroundColor: Colors.white,
        title: Text(
          lang.t('tab_mine'),
          style: const TextStyle(color: Colors.black),
        ),
        centerTitle: true,
      ),
      body: RefreshIndicator(
        color: mainBlue,
        onRefresh: () => _loadProfileData(silent: true),
        child: ListView(
          physics: const AlwaysScrollableScrollPhysics(),
          padding: const EdgeInsets.fromLTRB(16, 16, 16, 36),
          children: [
            _buildHeroCard(
              nickname: nickname,
              username: username,
              bio: bio,
              title: title,
              titleColor: titleColor,
              reputation: reputation,
            ),
            const SizedBox(height: 14),
            _buildQuickActions(lang),
            const SizedBox(height: 14),
            _buildArchiveSummary(),
            const SizedBox(height: 14),
            _buildInterestProfile(),
            const SizedBox(height: 14),
            _buildFavoritesSection(),
            const SizedBox(height: 14),
            _buildSubscriptionsSection(),
            const SizedBox(height: 14),
            _buildToolsSection(lang),
            const SizedBox(height: 18),
            OutlinedButton(
              onPressed: _logout,
              style: OutlinedButton.styleFrom(
                side: const BorderSide(color: Colors.red),
                padding: const EdgeInsets.symmetric(vertical: 14),
                shape: RoundedRectangleBorder(
                  borderRadius: BorderRadius.circular(14),
                ),
              ),
              child: Text(
                lang.t('logout'),
                style: const TextStyle(
                  color: Colors.red,
                  fontSize: 15,
                  fontWeight: FontWeight.w700,
                ),
              ),
            ),
            if (isRefreshing) ...[
              const SizedBox(height: 10),
              Center(
                child: Text(
                  _text('正在刷新个人数据...', 'Refreshing profile data...'),
                  style: const TextStyle(
                    fontSize: 12,
                    color: Color(0xFF64748B),
                  ),
                ),
              ),
            ],
          ],
        ),
      ),
    );
  }

  Widget _buildHeroCard({
    required String nickname,
    required String username,
    required String bio,
    required String title,
    required Color titleColor,
    required int reputation,
  }) {
    return Container(
      padding: const EdgeInsets.all(20),
      decoration: BoxDecoration(
        gradient: const LinearGradient(
          colors: [Color(0xFF0F172A), Color(0xFF0F4C75)],
          begin: Alignment.topLeft,
          end: Alignment.bottomRight,
        ),
        borderRadius: BorderRadius.circular(24),
        boxShadow: [
          BoxShadow(
            color: Colors.black.withOpacity(0.08),
            blurRadius: 22,
            offset: const Offset(0, 12),
          ),
        ],
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              CircleAvatar(
                radius: 32,
                backgroundColor: Colors.white.withOpacity(0.12),
                backgroundImage: NetworkImage(buildAvatarUrl(userProfile)),
              ),
              const SizedBox(width: 14),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      nickname,
                      style: const TextStyle(
                        color: Colors.white,
                        fontSize: 22,
                        fontWeight: FontWeight.w800,
                      ),
                    ),
                    const SizedBox(height: 4),
                    Text(
                      '@$username',
                      style: const TextStyle(
                        color: Color(0xFFBFDBFE),
                        fontSize: 13,
                        fontWeight: FontWeight.w600,
                      ),
                    ),
                    const SizedBox(height: 10),
                    Container(
                      padding: const EdgeInsets.symmetric(
                        horizontal: 10,
                        vertical: 6,
                      ),
                      decoration: BoxDecoration(
                        color: titleColor.withOpacity(0.18),
                        borderRadius: BorderRadius.circular(999),
                        border: Border.all(color: titleColor),
                      ),
                      child: Text(
                        title,
                        style: TextStyle(
                          color: titleColor,
                          fontSize: 12,
                          fontWeight: FontWeight.w700,
                        ),
                      ),
                    ),
                  ],
                ),
              ),
            ],
          ),
          const SizedBox(height: 16),
          Text(
            bio,
            style: const TextStyle(
              color: Color(0xFFE2E8F0),
              fontSize: 13,
              height: 1.6,
            ),
          ),
          const SizedBox(height: 18),
          Row(
            children: [
              Expanded(
                child: _buildDarkStat(
                  '${_readInt(userStats?['tweetCount'])}',
                  _text('已发布成果', 'Works'),
                ),
              ),
              const SizedBox(width: 10),
              Expanded(
                child: _buildDarkStat(
                  '${_readInt(userStats?['followingCount'])}',
                  _text('关注中', 'Following'),
                ),
              ),
              const SizedBox(width: 10),
              Expanded(
                child: _buildDarkStat(
                  '${_readInt(userStats?['followerCount'])}',
                  _text('粉丝', 'Followers'),
                ),
              ),
              const SizedBox(width: 10),
              Expanded(
                child: _buildDarkStat(
                  '$reputation',
                  _text('学术积分', 'Reputation'),
                ),
              ),
            ],
          ),
        ],
      ),
    );
  }

  Widget _buildQuickActions(LangProvider lang) {
    return Row(
      children: [
        Expanded(
          child: _buildActionTile(
            icon: Icons.auto_stories_outlined,
            title: lang.t('menu_manuscripts'),
            subtitle: _text('继续管理你的成果', 'Manage your published works'),
            onTap: () {
              Navigator.push(
                context,
                MaterialPageRoute(builder: (_) => const MyContentPage()),
              );
            },
          ),
        ),
        const SizedBox(width: 12),
        Expanded(
          child: _buildActionTile(
            icon: Icons.auto_awesome_outlined,
            title: lang.t('go_publish'),
            subtitle: _text('进入发布与 AI 辅助', 'Open publishing and AI helper'),
            onTap: _openPublish,
          ),
        ),
      ],
    );
  }

  Widget _buildArchiveSummary() {
    final direction = _displayText(
      archiveStats?['researchDirection'],
      fallback: _text('待形成研究主线', 'Direction still forming'),
    );
    final workCount = _readInt(archiveStats?['workCount']);
    final averageScore = _readDouble(archiveStats?['averageScore']);
    final totalDownloads = _readInt(archiveStats?['totalDownloads']);
    final areas = List<dynamic>.from(archiveStats?['topResearchAreas'] ?? []);
    final tags = List<dynamic>.from(archiveStats?['hotTags'] ?? []);

    return _buildSectionCard(
      title: _text('研究档案摘要', 'Research Archive'),
      subtitle: _text(
        '这里汇总 web 端档案页里的核心信息，方便在手机端快速回看整体研究布局。',
        'A compact mobile summary of the web archive page.',
      ),
      child: Column(
        children: [
          Row(
            children: [
              Expanded(
                child: _buildMetricPanel(
                  _text('研究方向', 'Direction'),
                  direction,
                  icon: Icons.explore_outlined,
                ),
              ),
              const SizedBox(width: 10),
              Expanded(
                child: _buildMetricPanel(
                  _text('平均评分', 'Avg Score'),
                  averageScore.toStringAsFixed(1),
                  icon: Icons.star_outline,
                ),
              ),
            ],
          ),
          const SizedBox(height: 10),
          Row(
            children: [
              Expanded(
                child: _buildMetricPanel(
                  _text('成果数量', 'Works'),
                  '$workCount',
                  icon: Icons.description_outlined,
                ),
              ),
              const SizedBox(width: 10),
              Expanded(
                child: _buildMetricPanel(
                  _text('总下载量', 'Downloads'),
                  '$totalDownloads',
                  icon: Icons.download_outlined,
                ),
              ),
            ],
          ),
          if (areas.isNotEmpty) ...[
            const SizedBox(height: 14),
            _buildWrapBlock(
              label: _text('主要领域', 'Top Areas'),
              values: areas.map((item) => item.toString()).toList(),
              chipColor: const Color(0xFFE0F2FE),
              textColor: const Color(0xFF0369A1),
            ),
          ],
          if (tags.isNotEmpty) ...[
            const SizedBox(height: 12),
            _buildWrapBlock(
              label: _text('热门标签', 'Hot Tags'),
              values: tags.map((item) => item.toString()).toList(),
              chipColor: const Color(0xFFECFCCB),
              textColor: const Color(0xFF3F6212),
            ),
          ],
        ],
      ),
    );
  }

  Widget _buildInterestProfile() {
    final dominant = _displayText(
      interestProfile?['dominantInterest'],
      fallback: _text('尚未形成', 'Not ready yet'),
    );
    final summary = _displayText(
      interestProfile?['profileSummary'],
      fallback: _text(
        '继续发布成果、收藏内容并订阅研究领域后，这里会逐渐形成更清晰的兴趣画像。',
        'Publish, bookmark, and subscribe to research areas to build a stronger interest profile.',
      ),
    );
    final ready = interestProfile?['interestProfileReady'] == true;
    final interestAreas = List<dynamic>.from(
      interestProfile?['interestAreas'] ?? [],
    );
    final publishedTopAreas = List<dynamic>.from(
      interestProfile?['publishedTopAreas'] ?? [],
    );
    final favoriteTopAreas = List<dynamic>.from(
      interestProfile?['favoriteTopAreas'] ?? [],
    );
    final followingAreaHints = List<dynamic>.from(
      interestProfile?['followingAreaHints'] ?? [],
    );

    return _buildSectionCard(
      title: _text('兴趣画像', 'Interest Profile'),
      subtitle: _text(
        '已接入 web 端同源画像接口，可用于推荐排序、搜索发现和个人偏好总结。',
        'Powered by the same backend profile used by web recommendations.',
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Container(
            width: double.infinity,
            padding: const EdgeInsets.all(14),
            decoration: BoxDecoration(
              color: ready ? const Color(0xFFF0F9FF) : const Color(0xFFF8FAFC),
              borderRadius: BorderRadius.circular(16),
              border: Border.all(
                color: ready
                    ? const Color(0xFFBAE6FD)
                    : const Color(0xFFE2E8F0),
              ),
            ),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  ready
                      ? _text(
                          '当前主兴趣：$dominant',
                          'Current dominant interest: $dominant',
                        )
                      : _text(
                          '兴趣画像仍在生成中',
                          'Interest profile is still warming up',
                        ),
                  style: const TextStyle(
                    fontSize: 15,
                    fontWeight: FontWeight.w800,
                    color: Color(0xFF0F172A),
                  ),
                ),
                const SizedBox(height: 8),
                Text(
                  summary,
                  style: const TextStyle(
                    fontSize: 13,
                    color: Color(0xFF475569),
                    height: 1.6,
                  ),
                ),
              ],
            ),
          ),
          if (interestAreas.isNotEmpty) ...[
            const SizedBox(height: 14),
            Wrap(
              spacing: 8,
              runSpacing: 8,
              children: interestAreas.take(6).map((item) {
                final map = item is Map
                    ? Map<String, dynamic>.from(item)
                    : <String, dynamic>{};
                final name = _displayText(map['name'] ?? map['label']);
                final score = _readDouble(map['score']);
                return Container(
                  padding: const EdgeInsets.symmetric(
                    horizontal: 12,
                    vertical: 8,
                  ),
                  decoration: BoxDecoration(
                    color: const Color(0xFFF8FBFF),
                    borderRadius: BorderRadius.circular(999),
                    border: Border.all(color: const Color(0xFFD8E8FB)),
                  ),
                  child: Text(
                    '$name ${score > 0 ? score.toStringAsFixed(1) : ''}'.trim(),
                    style: const TextStyle(
                      fontSize: 12,
                      fontWeight: FontWeight.w700,
                      color: Color(0xFF1D4ED8),
                    ),
                  ),
                );
              }).toList(),
            ),
          ],
          if (publishedTopAreas.isNotEmpty) ...[
            const SizedBox(height: 14),
            _buildTinySummary(
              _text('发布偏好', 'Published focus'),
              publishedTopAreas.map((item) => item.toString()).join(' / '),
            ),
          ],
          if (favoriteTopAreas.isNotEmpty) ...[
            const SizedBox(height: 8),
            _buildTinySummary(
              _text('收藏偏好', 'Favorite focus'),
              favoriteTopAreas.map((item) => item.toString()).join(' / '),
            ),
          ],
          if (followingAreaHints.isNotEmpty) ...[
            const SizedBox(height: 8),
            _buildTinySummary(
              _text('关注作者线索', 'Followed-author hints'),
              followingAreaHints.map((item) => item.toString()).join(' / '),
            ),
          ],
        ],
      ),
    );
  }

  Widget _buildFavoritesSection() {
    return _buildSectionCard(
      title: _text('我的收藏', 'My Favorites'),
      subtitle: _text(
        '详情页里的收藏已经和 web 端打通，这里会直接显示你的个人知识库内容。',
        'Favorites from detail pages are now shown here as your mobile knowledge base.',
      ),
      action: Container(
        padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
        decoration: BoxDecoration(
          color: const Color(0xFFF1F5F9),
          borderRadius: BorderRadius.circular(999),
        ),
        child: Text(
          '${favorites.length}',
          style: const TextStyle(
            fontWeight: FontWeight.w800,
            color: Color(0xFF0F172A),
          ),
        ),
      ),
      child: favorites.isEmpty
          ? _buildEmptyState(
              _text(
                '还没有收藏内容。去成果详情页点一下收藏，这里就会自动出现。',
                'No favorites yet. Bookmark works from detail pages and they will appear here.',
              ),
            )
          : Column(
              children: favorites.take(4).map((item) {
                final title = _displayText(
                  item['title'],
                  fallback: _text('未命名收藏成果', 'Untitled favorite work'),
                );
                final area = _displayText(
                  item['researchArea'],
                  fallback: _text('未分类领域', 'Uncategorized'),
                );
                final publicationType = _displayText(
                  item['publicationType'],
                  fallback: _text('未分类', 'Unclassified'),
                );
                final content = _displayText(
                  item['content'],
                  fallback: _text('暂无摘要', 'No abstract available'),
                );
                final tweetId = asInt(item['id']);
                return Container(
                  margin: const EdgeInsets.only(bottom: 10),
                  child: InkWell(
                    borderRadius: BorderRadius.circular(16),
                    onTap: tweetId == 0
                        ? null
                        : () => Navigator.push(
                            context,
                            MaterialPageRoute(
                              builder: (_) => TweetDetailPage(tweetId: tweetId),
                            ),
                          ),
                    child: Ink(
                      padding: const EdgeInsets.all(14),
                      decoration: BoxDecoration(
                        color: const Color(0xFFFFFBEB),
                        borderRadius: BorderRadius.circular(16),
                        border: Border.all(color: const Color(0xFFFDE68A)),
                      ),
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(
                            title,
                            style: const TextStyle(
                              fontSize: 15,
                              fontWeight: FontWeight.w800,
                            ),
                          ),
                          const SizedBox(height: 8),
                          Wrap(
                            spacing: 8,
                            runSpacing: 8,
                            children: [
                              _pill(
                                area,
                                const Color(0xFFFFF3C4),
                                const Color(0xFF92400E),
                              ),
                              _pill(
                                publicationType,
                                const Color(0xFFFDE68A),
                                const Color(0xFF92400E),
                              ),
                            ],
                          ),
                          const SizedBox(height: 10),
                          Text(
                            content,
                            maxLines: 3,
                            overflow: TextOverflow.ellipsis,
                            style: const TextStyle(
                              fontSize: 13,
                              height: 1.55,
                              color: Color(0xFF475569),
                            ),
                          ),
                          const SizedBox(height: 10),
                          Text(
                            _text(
                              '收藏于 ${_formatDate(item['favoriteCreateTime'] ?? item['createTime'])}',
                              'Saved on ${_formatDate(item['favoriteCreateTime'] ?? item['createTime'])}',
                            ),
                            style: const TextStyle(
                              fontSize: 12,
                              color: Color(0xFF78716C),
                              fontWeight: FontWeight.w600,
                            ),
                          ),
                        ],
                      ),
                    ),
                  ),
                );
              }).toList(),
            ),
    );
  }

  Widget _buildSubscriptionsSection() {
    return _buildSectionCard(
      title: _text('研究领域订阅', 'Research Subscriptions'),
      subtitle: _text(
        '搜索页和详情页里的订阅动作都会同步到这里，你可以直接在手机端管理。',
        'Subscriptions from search and detail pages can be managed here.',
      ),
      action: Container(
        padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
        decoration: BoxDecoration(
          color: const Color(0xFFEFF6FF),
          borderRadius: BorderRadius.circular(999),
        ),
        child: Text(
          '${subscriptions.length}',
          style: const TextStyle(
            fontWeight: FontWeight.w800,
            color: Color(0xFF1D4ED8),
          ),
        ),
      ),
      child: subscriptions.isEmpty
          ? _buildEmptyState(
              _text(
                '还没有订阅领域。现在可以从搜索页推荐卡片或详情页直接订阅。',
                'No subscriptions yet. You can now subscribe from search results or detail pages.',
              ),
            )
          : Column(
              children: subscriptions.map((item) {
                final area = _displayText(
                  item['researchArea'] ?? item['targetValue'],
                  fallback: _text('未命名领域', 'Unnamed area'),
                );
                return Container(
                  margin: const EdgeInsets.only(bottom: 10),
                  padding: const EdgeInsets.all(14),
                  decoration: BoxDecoration(
                    color: const Color(0xFFF8FBFF),
                    borderRadius: BorderRadius.circular(16),
                    border: Border.all(color: const Color(0xFFD8E8FB)),
                  ),
                  child: Row(
                    children: [
                      Container(
                        width: 38,
                        height: 38,
                        decoration: BoxDecoration(
                          color: const Color(0xFFE0F2FE),
                          borderRadius: BorderRadius.circular(12),
                        ),
                        alignment: Alignment.center,
                        child: const Icon(
                          Icons.notifications_active_outlined,
                          color: Color(0xFF0284C7),
                        ),
                      ),
                      const SizedBox(width: 12),
                      Expanded(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text(
                              area,
                              style: const TextStyle(
                                fontSize: 15,
                                fontWeight: FontWeight.w800,
                              ),
                            ),
                            const SizedBox(height: 4),
                            Text(
                              _text(
                                '订阅后，该领域新成果会进入你的通知流。',
                                'New works in this area will enter your notification feed.',
                              ),
                              style: const TextStyle(
                                fontSize: 12,
                                color: Color(0xFF64748B),
                                height: 1.5,
                              ),
                            ),
                          ],
                        ),
                      ),
                      const SizedBox(width: 10),
                      TextButton(
                        onPressed: isRemovingSubscription
                            ? null
                            : () => _removeSubscription(item),
                        child: Text(
                          _text('取消', 'Remove'),
                          style: const TextStyle(fontWeight: FontWeight.w700),
                        ),
                      ),
                    ],
                  ),
                );
              }).toList(),
            ),
    );
  }

  Widget _buildToolsSection(LangProvider lang) {
    return _buildSectionCard(
      title: _text('账户与创作工具', 'Account & Tools'),
      subtitle: _text(
        '把个人页里常用的入口集中到一起，减少在底部导航和详情页之间来回切换。',
        'Common profile tools collected in one place for faster mobile navigation.',
      ),
      child: Column(
        children: [
          _buildMenuItem(
            icon: Icons.edit_outlined,
            title: lang.t('menu_edit_profile'),
            subtitle: _text('修改昵称、简介和头像', 'Update nickname, bio, and avatar'),
            onTap: () {
              Navigator.push(
                context,
                MaterialPageRoute(builder: (_) => const EditProfilePage()),
              ).then((result) {
                if (result == true) {
                  _loadProfileData(silent: true);
                }
              });
            },
          ),
          const SizedBox(height: 10),
          _buildMenuItem(
            icon: Icons.article_outlined,
            title: lang.t('menu_manuscripts'),
            subtitle: _text(
              '继续编辑和管理自己发布的成果',
              'Review and manage your published works',
            ),
            onTap: () {
              Navigator.push(
                context,
                MaterialPageRoute(builder: (_) => const MyContentPage()),
              );
            },
          ),
          const SizedBox(height: 10),
          _buildMenuItem(
            icon: Icons.publish_outlined,
            title: lang.t('creator_center'),
            subtitle: _text(
              '继续发布新成果，并使用 AI 辅助填写',
              'Publish a new work with AI-assisted input',
            ),
            onTap: _openPublish,
          ),
        ],
      ),
    );
  }

  Widget _buildDarkStat(String value, String label) {
    return Container(
      padding: const EdgeInsets.symmetric(vertical: 12),
      decoration: BoxDecoration(
        color: Colors.white.withOpacity(0.08),
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: Colors.white.withOpacity(0.08)),
      ),
      child: Column(
        children: [
          Text(
            value,
            style: const TextStyle(
              color: Colors.white,
              fontSize: 18,
              fontWeight: FontWeight.w800,
            ),
          ),
          const SizedBox(height: 4),
          Text(
            label,
            style: const TextStyle(
              color: Color(0xFFBFDBFE),
              fontSize: 12,
              fontWeight: FontWeight.w600,
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildActionTile({
    required IconData icon,
    required String title,
    required String subtitle,
    required VoidCallback onTap,
  }) {
    return InkWell(
      borderRadius: BorderRadius.circular(18),
      onTap: onTap,
      child: Ink(
        padding: const EdgeInsets.all(16),
        decoration: BoxDecoration(
          color: Colors.white,
          borderRadius: BorderRadius.circular(18),
          border: Border.all(color: const Color(0xFFE2E8F0)),
        ),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Container(
              width: 42,
              height: 42,
              decoration: BoxDecoration(
                color: const Color(0xFFE8F5FE),
                borderRadius: BorderRadius.circular(14),
              ),
              alignment: Alignment.center,
              child: Icon(icon, color: mainBlue),
            ),
            const SizedBox(height: 12),
            Text(
              title,
              style: const TextStyle(fontSize: 15, fontWeight: FontWeight.w800),
            ),
            const SizedBox(height: 6),
            Text(
              subtitle,
              style: const TextStyle(
                fontSize: 12,
                color: Color(0xFF64748B),
                height: 1.5,
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildSectionCard({
    required String title,
    required String subtitle,
    required Widget child,
    Widget? action,
  }) {
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(22),
        border: Border.all(color: const Color(0xFFE2E8F0)),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      title,
                      style: const TextStyle(
                        fontSize: 17,
                        fontWeight: FontWeight.w800,
                      ),
                    ),
                    const SizedBox(height: 6),
                    Text(
                      subtitle,
                      style: const TextStyle(
                        fontSize: 12,
                        color: Color(0xFF64748B),
                        height: 1.55,
                      ),
                    ),
                  ],
                ),
              ),
              if (action != null) ...[const SizedBox(width: 12), action],
            ],
          ),
          const SizedBox(height: 14),
          child,
        ],
      ),
    );
  }

  Widget _buildMetricPanel(
    String label,
    String value, {
    required IconData icon,
  }) {
    return Container(
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: const Color(0xFFF8FAFC),
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: const Color(0xFFE2E8F0)),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Icon(icon, size: 18, color: const Color(0xFF2563EB)),
          const SizedBox(height: 10),
          Text(
            label,
            style: const TextStyle(
              fontSize: 12,
              color: Color(0xFF64748B),
              fontWeight: FontWeight.w600,
            ),
          ),
          const SizedBox(height: 6),
          Text(
            value,
            style: const TextStyle(
              fontSize: 16,
              fontWeight: FontWeight.w800,
              color: Color(0xFF0F172A),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildWrapBlock({
    required String label,
    required List<String> values,
    required Color chipColor,
    required Color textColor,
  }) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          label,
          style: const TextStyle(
            fontSize: 12,
            fontWeight: FontWeight.w700,
            color: Color(0xFF475569),
          ),
        ),
        const SizedBox(height: 8),
        Wrap(
          spacing: 8,
          runSpacing: 8,
          children: values
              .map((value) => _pill(value, chipColor, textColor))
              .toList(),
        ),
      ],
    );
  }

  Widget _buildTinySummary(String label, String value) {
    return Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        SizedBox(
          width: 86,
          child: Text(
            label,
            style: const TextStyle(
              fontSize: 12,
              color: Color(0xFF64748B),
              fontWeight: FontWeight.w700,
            ),
          ),
        ),
        Expanded(
          child: Text(
            value,
            style: const TextStyle(
              fontSize: 12,
              color: Color(0xFF334155),
              height: 1.55,
            ),
          ),
        ),
      ],
    );
  }

  Widget _buildMenuItem({
    required IconData icon,
    required String title,
    required String subtitle,
    required VoidCallback onTap,
  }) {
    return InkWell(
      borderRadius: BorderRadius.circular(16),
      onTap: onTap,
      child: Ink(
        padding: const EdgeInsets.all(14),
        decoration: BoxDecoration(
          color: const Color(0xFFF8FAFC),
          borderRadius: BorderRadius.circular(16),
          border: Border.all(color: const Color(0xFFE2E8F0)),
        ),
        child: Row(
          children: [
            Container(
              width: 40,
              height: 40,
              decoration: BoxDecoration(
                color: Colors.white,
                borderRadius: BorderRadius.circular(12),
              ),
              alignment: Alignment.center,
              child: Icon(icon, color: const Color(0xFF334155)),
            ),
            const SizedBox(width: 12),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    title,
                    style: const TextStyle(
                      fontSize: 15,
                      fontWeight: FontWeight.w800,
                    ),
                  ),
                  const SizedBox(height: 4),
                  Text(
                    subtitle,
                    style: const TextStyle(
                      fontSize: 12,
                      color: Color(0xFF64748B),
                      height: 1.5,
                    ),
                  ),
                ],
              ),
            ),
            const Icon(Icons.chevron_right, color: Color(0xFF94A3B8)),
          ],
        ),
      ),
    );
  }

  Widget _buildEmptyState(String text) {
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: const Color(0xFFF8FAFC),
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: const Color(0xFFE2E8F0)),
      ),
      child: Text(
        text,
        style: const TextStyle(
          fontSize: 13,
          color: Color(0xFF64748B),
          height: 1.55,
        ),
      ),
    );
  }

  Widget _pill(String text, Color bgColor, Color textColor) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
      decoration: BoxDecoration(
        color: bgColor,
        borderRadius: BorderRadius.circular(999),
      ),
      child: Text(
        text,
        style: TextStyle(
          fontSize: 11,
          color: textColor,
          fontWeight: FontWeight.w700,
        ),
      ),
    );
  }
}
