import 'package:dio/dio.dart';
import 'package:fl_chart/fl_chart.dart';
import 'package:flutter/material.dart';
import 'package:flutter_rating_bar/flutter_rating_bar.dart';
import 'package:provider/provider.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:url_launcher/url_launcher.dart';

import 'api_config.dart';
import 'app_lang.dart';
import 'api_helpers.dart';

class TweetDetailPage extends StatefulWidget {
  final int tweetId;

  const TweetDetailPage({super.key, required this.tweetId});

  @override
  State<TweetDetailPage> createState() => _TweetDetailPageState();
}

class _TweetDetailPageState extends State<TweetDetailPage> {
  final Color mainBlue = const Color(0xFF00A1D6);
  final TextEditingController _commentCtrl = TextEditingController();
  final TextEditingController _replyCtrl = TextEditingController();

  Map<String, dynamic>? tweetDetail;
  List<Map<String, dynamic>> comments = [];
  List<Map<String, dynamic>> relatedTweets = [];
  List<Map<String, dynamic>> mySubscriptions = [];
  bool isLoading = true;
  bool isAiLoading = false;
  bool isBookmarked = false;
  bool isReplySending = false;
  bool isSubscribing = false;
  int currentUserId = 0;
  int? activeReplyCommentId;
  String aiResult = '';
  String commentType = 'discussion';
  String subscriptionStatus = '';

  double score1 = 0;
  double score2 = 0;
  double score3 = 0;
  double score4 = 0;
  double score5 = 0;

  double avgScore = 0;
  Map<String, dynamic> radar = {};
  Map<String, dynamic> signals = {};

  @override
  void initState() {
    super.initState();
    _fetchAllData();
  }

  @override
  void dispose() {
    _commentCtrl.dispose();
    _replyCtrl.dispose();
    super.dispose();
  }

  Future<void> _fetchAllData() async {
    try {
      final prefs = await SharedPreferences.getInstance();
      currentUserId = prefs.getInt('userId') ?? 0;

      final responses = await Future.wait([
        createSessionDio().get(
          '$baseUrl/tweets/${widget.tweetId}/detail-dto',
          queryParameters: {'userId': currentUserId},
        ),
        createSessionDio().get(
          '$baseUrl/tweets/${widget.tweetId}/comments',
          queryParameters: {'userId': currentUserId},
        ),
        createSessionDio().get('$baseUrl/tweets/${widget.tweetId}/related'),
        if (currentUserId > 0)
          createSessionDio().get(
            '$baseUrl/tweets/${widget.tweetId}/bookmark-status',
            queryParameters: {'userId': currentUserId},
          ),
        if (currentUserId > 0)
          createSessionDio().get(
            '$baseUrl/subscriptions/my',
            queryParameters: {'userId': currentUserId},
          ),
      ]);

      final detail = asMap(responses[0].data);
      final nextComments = asMapList(responses[1].data);
      final nextRelated = asMapList(responses[2].data);

      final myRating = detail['myRating'] is Map
          ? asMap(detail['myRating'])
          : const <String, dynamic>{};
      final nextRadar = asMap(detail['radar']);
      final nextSignals = asMap(detail['signals']);

      if (!mounted) {
        return;
      }

      setState(() {
        tweetDetail = detail;
        comments = nextComments;
        relatedTweets = nextRelated;
        radar = nextRadar;
        signals = nextSignals;
        avgScore = double.tryParse(detail['avgScore'].toString()) ?? 0;
        score1 = _toDouble(myRating['score1']);
        score2 = _toDouble(myRating['score2']);
        score3 = _toDouble(myRating['score3']);
        score4 = _toDouble(myRating['score4']);
        score5 = _toDouble(myRating['score5']);
        isBookmarked =
            responses.length > 3 &&
            ((asMap(responses[3].data)['isFavorited'] == true) ||
                (asMap(responses[3].data)['favorited'] == true));
        mySubscriptions = responses.length > 4
            ? asMapList(responses[4].data)
            : <Map<String, dynamic>>[];
        isLoading = false;
      });
    } catch (_) {
      if (!mounted) {
        return;
      }
      setState(() => isLoading = false);
    }
  }

  Future<void> _downloadFile() async {
    final url = Uri.parse('$baseUrl/tweets/${widget.tweetId}/download');
    if (await canLaunchUrl(url)) {
      await launchUrl(url, mode: LaunchMode.externalApplication);
      await _fetchAllData();
    }
  }

  Future<void> _toggleBookmark() async {
    if (currentUserId == 0) {
      return;
    }
    try {
      final res = await createSessionDio().post(
        '$baseUrl/tweets/${widget.tweetId}/bookmark',
        queryParameters: {'userId': currentUserId},
      );
      if (!mounted) {
        return;
      }
      setState(() {
        isBookmarked =
            res.data['isFavorited'] == true || res.data['favorited'] == true;
        if (res.data['signals'] is Map) {
          signals = asMap(res.data['signals']);
        }
      });
      await _fetchAllData();
    } catch (_) {}
  }

  Future<void> _shareResearch() async {
    try {
      await createSessionDio().post('$baseUrl/tweets/${widget.tweetId}/share');
      await _fetchAllData();
    } catch (_) {}
  }

  String _currentResearchArea() {
    final tweet = tweetDetail?['tweet'] is Map
        ? asMap(tweetDetail!['tweet'])
        : const <String, dynamic>{};
    return (tweet['researchArea'] ?? '').toString().trim();
  }

  bool _isSubscribedArea(String area) {
    final normalized = area.trim().toLowerCase();
    return mySubscriptions.any((item) {
      final value = (item['researchArea'] ?? item['targetValue'] ?? '')
          .toString()
          .trim()
          .toLowerCase();
      return value == normalized;
    });
  }

  Future<void> _subscribeCurrentArea() async {
    final area = _currentResearchArea();
    if (currentUserId == 0 || area.isEmpty || isSubscribing) {
      return;
    }
    if (_isSubscribedArea(area)) {
      setState(() {
        subscriptionStatus =
            'You have already subscribed to "$area". Future work in this area will notify you.';
      });
      return;
    }

    setState(() {
      isSubscribing = true;
      subscriptionStatus = 'Subscribing you to "$area"...';
    });

    try {
      await createSessionDio().post(
        '$baseUrl/subscriptions',
        queryParameters: {'userId': currentUserId, 'researchArea': area},
      );
      await _fetchAllData();
      if (!mounted) return;
      setState(() {
        subscriptionStatus =
            'Subscribed successfully. Future work in "$area" will notify you.';
      });
    } catch (_) {
      if (!mounted) return;
      setState(() {
        subscriptionStatus = 'Subscription failed. Please try again later.';
      });
    } finally {
      if (mounted) {
        setState(() => isSubscribing = false);
      }
    }
  }

  Future<void> _submitRating() async {
    final lang = context.read<LangProvider>();
    if (currentUserId == 0) {
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(SnackBar(content: Text(lang.t('login_expired'))));
      return;
    }

    try {
      await createSessionDio().post(
        '$baseUrl/tweets/${widget.tweetId}/rate',
        data: FormData.fromMap({
          'userId': currentUserId,
          's1': score1.round(),
          's2': score2.round(),
          's3': score3.round(),
          's4': score4.round(),
          's5': score5.round(),
        }),
      );
      if (!mounted) {
        return;
      }
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(SnackBar(content: Text(lang.t('rating_success'))));
      await _fetchAllData();
    } catch (e) {
      if (!mounted) {
        return;
      }
      ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text('$e')));
    }
  }

  Future<void> _callAi() async {
    final lang = context.read<LangProvider>();
    setState(() => isAiLoading = true);
    final langParam = lang.currentLang == 'zh' ? 'zh' : 'en';

    try {
      final res = await createSessionDio().post(
        '$baseUrl/ai/evaluate/${widget.tweetId}',
        queryParameters: {'lang': langParam},
      );
      if (!mounted) {
        return;
      }
      setState(() {
        aiResult = (res.data['result'] ?? '')
            .toString()
            .replaceAll('<br>', '\n')
            .replaceAll(RegExp(r'<[^>]*>'), '');
      });
    } catch (e) {
      if (!mounted) {
        return;
      }
      setState(() => aiResult = 'AI Error: $e');
    } finally {
      if (mounted) {
        setState(() => isAiLoading = false);
      }
    }
  }

  Future<void> _sendComment() async {
    if (_commentCtrl.text.trim().isEmpty || currentUserId == 0) {
      return;
    }

    try {
      await createSessionDio().post(
        '$baseUrl/tweets/${widget.tweetId}/comments',
        data: FormData.fromMap({
          'userId': currentUserId,
          'content': _commentCtrl.text.trim(),
          'commentType': commentType,
        }),
      );
      _commentCtrl.clear();
      FocusScope.of(context).unfocus();
      await _fetchAllData();
    } catch (_) {}
  }

  Future<void> _sendReply(Map<String, dynamic> comment) async {
    if (_replyCtrl.text.trim().isEmpty || currentUserId == 0) {
      return;
    }

    final parentId = asInt(comment['parentId'], fallback: asInt(comment['id']));
    final replyUser = asMap(comment['user']);
    final replyToUserId = asInt(replyUser['id']);

    setState(() => isReplySending = true);

    try {
      await createSessionDio().post(
        '$baseUrl/tweets/${widget.tweetId}/comments',
        data: FormData.fromMap({
          'userId': currentUserId,
          'content': _replyCtrl.text.trim(),
          'parentId': parentId > 0 ? parentId : null,
          'replyToUserId': replyToUserId > 0 ? replyToUserId : null,
          'commentType': 'discussion',
        }),
      );
      _replyCtrl.clear();
      if (!mounted) {
        return;
      }
      setState(() => activeReplyCommentId = null);
      await _fetchAllData();
    } catch (_) {
      if (mounted) {
        setState(() => isReplySending = false);
      }
    } finally {
      if (mounted) {
        setState(() => isReplySending = false);
      }
    }
  }

  Future<void> _toggleCommentLike(Map<String, dynamic> comment) async {
    if (currentUserId == 0) {
      return;
    }
    final commentId = asInt(comment['id'], fallback: -1);
    if (commentId <= 0) {
      return;
    }
    try {
      await createSessionDio().post(
        '$baseUrl/tweets/comments/$commentId/like',
        queryParameters: {'userId': currentUserId},
      );
      await _fetchAllData();
    } catch (_) {}
  }

  Future<void> _toggleCommentAccepted(Map<String, dynamic> comment) async {
    if (currentUserId == 0) {
      return;
    }
    final commentId = asInt(comment['id'], fallback: -1);
    if (commentId <= 0) {
      return;
    }
    try {
      await createSessionDio().post(
        '$baseUrl/tweets/comments/$commentId/accept',
        queryParameters: {'userId': currentUserId},
      );
      await _fetchAllData();
    } catch (_) {}
  }

  @override
  Widget build(BuildContext context) {
    final lang = context.watch<LangProvider>();
    if (isLoading) {
      return const Scaffold(body: Center(child: CircularProgressIndicator()));
    }

    if (tweetDetail == null) {
      return Scaffold(
        appBar: AppBar(title: Text(lang.t('detail_title'))),
        body: const Center(child: Text('Load failed')),
      );
    }

    final tweet = asMap(tweetDetail!['tweet']);
    final author = asMap(tweet['author']);
    final bool isAuthor = author['id'] == currentUserId;

    return Scaffold(
      appBar: AppBar(title: Text(lang.t('detail_title'))),
      body: RefreshIndicator(
        onRefresh: _fetchAllData,
        child: ListView(
          padding: const EdgeInsets.all(16),
          children: [
            _buildHeroCard(tweet, author, lang),
            const SizedBox(height: 16),
            _buildSubscriptionCard(tweet),
            const SizedBox(height: 16),
            _buildMetadataCard(tweet),
            const SizedBox(height: 16),
            _buildAiCard(lang),
            const SizedBox(height: 16),
            if (isAuthor)
              _buildAuthorDashboard(lang)
            else
              _buildReaderRating(lang),
            const SizedBox(height: 16),
            _buildRelatedSection(),
            const SizedBox(height: 16),
            _buildCommentComposer(lang),
            const SizedBox(height: 12),
            _buildCommentsList(),
            const SizedBox(height: 24),
          ],
        ),
      ),
    );
  }

  Widget _buildHeroCard(
    Map<String, dynamic> tweet,
    Map<String, dynamic> author,
    LangProvider lang,
  ) {
    final title = (tweet['title'] ?? '').toString().trim();
    final content = (tweet['content'] ?? '').toString();
    final originalFilename = (tweet['originalFilename'] ?? '').toString();
    final tags = (tweet['tags'] ?? '').toString();

    return Container(
      padding: const EdgeInsets.all(18),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(16),
        boxShadow: [
          BoxShadow(color: Colors.black.withOpacity(0.05), blurRadius: 12),
        ],
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              CircleAvatar(
                radius: 22,
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
                      ),
                    ),
                    Text(
                      (tweet['createTime'] ?? '').toString().split('T')[0],
                      style: const TextStyle(color: Colors.grey, fontSize: 12),
                    ),
                  ],
                ),
              ),
              Container(
                padding: const EdgeInsets.symmetric(
                  horizontal: 10,
                  vertical: 6,
                ),
                decoration: BoxDecoration(
                  color: const Color(0xFFFFF7D6),
                  borderRadius: BorderRadius.circular(999),
                  border: Border.all(color: const Color(0xFFFDE68A)),
                ),
                child: Text(
                  'Score ${avgScore.toStringAsFixed(1)}',
                  style: const TextStyle(fontWeight: FontWeight.bold),
                ),
              ),
            ],
          ),
          const SizedBox(height: 16),
          if (title.isNotEmpty)
            Text(
              title,
              style: const TextStyle(fontSize: 22, fontWeight: FontWeight.bold),
            ),
          if (title.isNotEmpty) const SizedBox(height: 10),
          Text(content, style: const TextStyle(fontSize: 15, height: 1.6)),
          if (tags.isNotEmpty) ...[
            const SizedBox(height: 14),
            Wrap(
              spacing: 8,
              runSpacing: 8,
              children: tags
                  .split(',')
                  .map((tag) => tag.trim())
                  .where((tag) => tag.isNotEmpty)
                  .map(
                    (tag) => Container(
                      padding: const EdgeInsets.symmetric(
                        horizontal: 10,
                        vertical: 4,
                      ),
                      decoration: BoxDecoration(
                        color: const Color(0xFFE8F5FE),
                        borderRadius: BorderRadius.circular(999),
                      ),
                      child: Text(
                        '#${lang.tTag(tag)}',
                        style: TextStyle(color: mainBlue, fontSize: 12),
                      ),
                    ),
                  )
                  .toList(),
            ),
          ],
          if (originalFilename.isNotEmpty) ...[
            const SizedBox(height: 16),
            InkWell(
              onTap: _downloadFile,
              child: Container(
                padding: const EdgeInsets.all(12),
                decoration: BoxDecoration(
                  color: mainBlue.withOpacity(0.08),
                  borderRadius: BorderRadius.circular(12),
                  border: Border.all(color: mainBlue.withOpacity(0.2)),
                ),
                child: Row(
                  children: [
                    Icon(Icons.description, color: mainBlue, size: 30),
                    const SizedBox(width: 12),
                    Expanded(
                      child: Text(
                        originalFilename,
                        style: TextStyle(
                          color: mainBlue,
                          fontWeight: FontWeight.bold,
                        ),
                      ),
                    ),
                    Icon(Icons.download_rounded, color: mainBlue),
                  ],
                ),
              ),
            ),
          ],
          const SizedBox(height: 14),
          Wrap(
            spacing: 8,
            runSpacing: 8,
            children: [
              _buildMetricChip(
                Icons.thumb_up_alt_outlined,
                '${signals['likes'] ?? 0}',
              ),
              _buildMetricChip(
                Icons.mode_comment_outlined,
                '${signals['comments'] ?? 0}',
              ),
              _buildMetricChip(
                Icons.download_outlined,
                '${signals['downloads'] ?? 0}',
              ),
              _buildMetricChip(
                Icons.remove_red_eye_outlined,
                '${signals['views'] ?? 0}',
              ),
              _buildMetricChip(
                Icons.bookmark_outline,
                '${signals['bookmarks'] ?? 0}',
              ),
              _buildMetricChip(
                Icons.local_fire_department_outlined,
                '${signals['hotScore'] ?? 0}',
              ),
            ],
          ),
          const SizedBox(height: 14),
          Wrap(
            spacing: 10,
            runSpacing: 10,
            children: [
              _buildActionButton(
                icon: Icons.share_outlined,
                label: lang.t('share'),
                onTap: _shareResearch,
              ),
              _buildActionButton(
                icon: isBookmarked ? Icons.bookmark : Icons.bookmark_border,
                label: isBookmarked ? 'Bookmarked' : 'Bookmark',
                onTap: _toggleBookmark,
              ),
              if (originalFilename.isNotEmpty)
                _buildActionButton(
                  icon: Icons.download_outlined,
                  label: lang.t('download_file'),
                  onTap: _downloadFile,
                ),
            ],
          ),
        ],
      ),
    );
  }

  Widget _buildMetadataCard(Map<String, dynamic> tweet) {
    final items = <MapEntry<String, String>>[
      MapEntry('Authors', (tweet['authors'] ?? '').toString()),
      MapEntry('Institution', (tweet['institution'] ?? '').toString()),
      MapEntry('Research Area', (tweet['researchArea'] ?? '').toString()),
      MapEntry('Keywords', (tweet['keywords'] ?? '').toString()),
      MapEntry('Content Type', (tweet['contentType'] ?? '').toString()),
      MapEntry('Publication Type', (tweet['publicationType'] ?? '').toString()),
      MapEntry('Status', (tweet['status'] ?? '').toString()),
      MapEntry('Language', (tweet['language'] ?? '').toString()),
      MapEntry('Venue', (tweet['journalOrConference'] ?? '').toString()),
      MapEntry('Date', (tweet['publicationDate'] ?? '').toString()),
      MapEntry('DOI', (tweet['doi'] ?? '').toString()),
      MapEntry('Visibility', (tweet['visibility'] ?? '').toString()),
      MapEntry('References', (tweet['referencesText'] ?? '').toString()),
      MapEntry('Project Links', (tweet['projectLinks'] ?? '').toString()),
    ].where((entry) => entry.value.trim().isNotEmpty).toList();

    if (items.isEmpty) {
      return const SizedBox.shrink();
    }

    return Container(
      padding: const EdgeInsets.all(18),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(16),
        boxShadow: [
          BoxShadow(color: Colors.black.withOpacity(0.05), blurRadius: 12),
        ],
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Text(
            'Research Metadata',
            style: TextStyle(fontSize: 16, fontWeight: FontWeight.bold),
          ),
          const SizedBox(height: 14),
          ...items.map(
            (entry) => Padding(
              padding: const EdgeInsets.only(bottom: 10),
              child: Row(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  SizedBox(
                    width: 120,
                    child: Text(
                      entry.key,
                      style: const TextStyle(
                        color: Colors.grey,
                        fontWeight: FontWeight.w600,
                      ),
                    ),
                  ),
                  Expanded(
                    child: Text(
                      entry.value,
                      style: const TextStyle(height: 1.5),
                    ),
                  ),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildSubscriptionCard(Map<String, dynamic> tweet) {
    final area = (tweet['researchArea'] ?? '').toString().trim();
    if (area.isEmpty) {
      return const SizedBox.shrink();
    }

    final subscribed = _isSubscribedArea(area);
    final statusText = subscriptionStatus.isNotEmpty
        ? subscriptionStatus
        : currentUserId == 0
        ? 'Please log in before subscribing to this research area.'
        : subscribed
        ? 'You have already subscribed to "$area". Future work in this area will notify you.'
        : 'Subscribe to "$area" to receive in-app notifications when new work is published.';

    return Container(
      padding: const EdgeInsets.all(18),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(16),
        boxShadow: [
          BoxShadow(color: Colors.black.withOpacity(0.05), blurRadius: 12),
        ],
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Text(
            'Research Subscription',
            style: TextStyle(fontSize: 16, fontWeight: FontWeight.bold),
          ),
          const SizedBox(height: 12),
          Container(
            width: double.infinity,
            padding: const EdgeInsets.all(14),
            decoration: BoxDecoration(
              color: const Color(0xFFF8FBFF),
              borderRadius: BorderRadius.circular(14),
              border: Border.all(color: const Color(0xFFD8E8FB)),
            ),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  'Current research area: $area',
                  style: const TextStyle(
                    fontSize: 14,
                    fontWeight: FontWeight.w700,
                    color: Color(0xFF1E3A8A),
                  ),
                ),
                const SizedBox(height: 8),
                Text(
                  statusText,
                  style: TextStyle(
                    height: 1.55,
                    fontSize: 13,
                    color: statusText.contains('failed')
                        ? const Color(0xFFB42318)
                        : const Color(0xFF475569),
                  ),
                ),
              ],
            ),
          ),
          const SizedBox(height: 12),
          Wrap(
            spacing: 10,
            runSpacing: 10,
            children: [
              _buildActionButton(
                icon: subscribed ? Icons.check_circle_outline : Icons.add_alert,
                label: currentUserId == 0
                    ? 'Login to Subscribe'
                    : subscribed
                    ? 'Subscribed'
                    : isSubscribing
                    ? 'Subscribing...'
                    : 'Subscribe Area',
                onTap: (currentUserId == 0 || subscribed || isSubscribing)
                    ? null
                    : _subscribeCurrentArea,
              ),
            ],
          ),
        ],
      ),
    );
  }

  Widget _buildAiCard(LangProvider lang) {
    return Container(
      padding: const EdgeInsets.all(18),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(16),
        boxShadow: [
          BoxShadow(color: Colors.black.withOpacity(0.05), blurRadius: 12),
        ],
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Text(
                lang.t('ai_review'),
                style: const TextStyle(
                  fontSize: 16,
                  fontWeight: FontWeight.bold,
                ),
              ),
              ElevatedButton.icon(
                onPressed: isAiLoading ? null : _callAi,
                style: ElevatedButton.styleFrom(
                  backgroundColor: const Color(0xFF6366F1),
                  foregroundColor: Colors.white,
                ),
                icon: const Icon(Icons.auto_awesome, size: 16),
                label: Text(
                  isAiLoading
                      ? lang.t('ai_btn_analyzing')
                      : lang.t('ai_btn_start'),
                ),
              ),
            ],
          ),
          const SizedBox(height: 12),
          Text(
            aiResult.isEmpty ? lang.t('ai_placeholder') : aiResult,
            style: TextStyle(
              color: aiResult.isEmpty ? Colors.grey : Colors.black87,
              height: 1.6,
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildAuthorDashboard(LangProvider lang) {
    final axes = _axisItems();
    final strongest = axes.reduce((a, b) => a.value >= b.value ? a : b);
    final weakest = axes.reduce((a, b) => a.value <= b.value ? a : b);

    return Container(
      padding: const EdgeInsets.all(20),
      decoration: BoxDecoration(
        gradient: const LinearGradient(
          begin: Alignment.topLeft,
          end: Alignment.bottomRight,
          colors: [Color(0xFFF9FCFF), Color(0xFFF3F8FE)],
        ),
        borderRadius: BorderRadius.circular(24),
        border: Border.all(color: const Color(0xFFDCEAF7)),
        boxShadow: [
          BoxShadow(
            color: const Color(0xFF0F172A).withOpacity(0.06),
            blurRadius: 18,
            offset: const Offset(0, 8),
          ),
        ],
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
                    Container(
                      padding: const EdgeInsets.symmetric(
                        horizontal: 10,
                        vertical: 6,
                      ),
                      decoration: BoxDecoration(
                        color: Colors.white,
                        borderRadius: BorderRadius.circular(999),
                        border: Border.all(color: const Color(0xFFD7E6F3)),
                      ),
                      child: Text(
                        lang.t('impact_dashboard'),
                        style: const TextStyle(
                          fontSize: 12,
                          fontWeight: FontWeight.w700,
                          color: Color(0xFF245D85),
                        ),
                      ),
                    ),
                    const SizedBox(height: 10),
                    const Text(
                      'Community evaluation snapshot',
                      style: TextStyle(
                        fontSize: 22,
                        fontWeight: FontWeight.w800,
                        color: Color(0xFF0F172A),
                      ),
                    ),
                    const SizedBox(height: 8),
                    Text(
                      'Strongest signal: ${strongest.label}. '
                      'Most room to improve: ${weakest.label}.',
                      style: const TextStyle(
                        fontSize: 13,
                        height: 1.5,
                        color: Color(0xFF5B6472),
                      ),
                    ),
                  ],
                ),
              ),
              const SizedBox(width: 12),
              Container(
                padding: const EdgeInsets.symmetric(
                  horizontal: 14,
                  vertical: 10,
                ),
                decoration: BoxDecoration(
                  color: const Color(0xFF10263D),
                  borderRadius: BorderRadius.circular(18),
                ),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    const Text(
                      'Average',
                      style: TextStyle(fontSize: 11, color: Color(0xFFB5C7D8)),
                    ),
                    const SizedBox(height: 2),
                    Text(
                      avgScore.toStringAsFixed(1),
                      style: const TextStyle(
                        fontSize: 26,
                        fontWeight: FontWeight.w800,
                        color: Colors.white,
                      ),
                    ),
                  ],
                ),
              ),
            ],
          ),
          const SizedBox(height: 18),
          Container(
            padding: const EdgeInsets.all(16),
            decoration: BoxDecoration(
              color: Colors.white,
              borderRadius: BorderRadius.circular(22),
              border: Border.all(color: const Color(0xFFE2ECF5)),
            ),
            child: SizedBox(
              height: 260,
              child: RadarChart(
                RadarChartData(
                  dataSets: [
                    RadarDataSet(
                      fillColor: const Color(0x3300A1D6),
                      borderColor: mainBlue,
                      entryRadius: 3,
                      borderWidth: 3,
                      dataEntries: axes
                          .map((axis) => RadarEntry(value: axis.value))
                          .toList(),
                    ),
                  ],
                  radarBackgroundColor: Colors.transparent,
                  radarShape: RadarShape.polygon,
                  borderData: FlBorderData(show: false),
                  radarBorderData: const BorderSide(color: Colors.transparent),
                  tickCount: 4,
                  ticksTextStyle: const TextStyle(
                    color: Colors.transparent,
                    fontSize: 0,
                  ),
                  gridBorderData: const BorderSide(
                    color: Color(0xFFE5EEF7),
                    width: 1,
                  ),
                  titlePositionPercentageOffset: 0.16,
                  titleTextStyle: const TextStyle(
                    color: Color(0xFF5B6472),
                    fontSize: 11,
                    fontWeight: FontWeight.w700,
                  ),
                  getTitle: (index, _) =>
                      RadarChartTitle(text: axes[index].label),
                ),
              ),
            ),
          ),
          const SizedBox(height: 16),
          GridView.count(
            crossAxisCount: 2,
            mainAxisSpacing: 10,
            crossAxisSpacing: 10,
            shrinkWrap: true,
            physics: const NeverScrollableScrollPhysics(),
            childAspectRatio: 1.75,
            children: [
              _buildDashboardStatCard(
                icon: Icons.people_alt_outlined,
                label: 'Ratings',
                value: '${radar['ratingCount'] ?? 0}',
                color: const Color(0xFF0EA5E9),
              ),
              _buildDashboardStatCard(
                icon: Icons.workspace_premium_outlined,
                label: 'Experts',
                value: '${radar['expertCount'] ?? 0}',
                color: const Color(0xFFF59E0B),
              ),
              _buildDashboardStatCard(
                icon: Icons.verified_outlined,
                label: 'Expert score',
                value: '${radar['expertScore'] ?? 0}',
                color: const Color(0xFF8B5CF6),
              ),
              _buildDashboardStatCard(
                icon: Icons.local_fire_department_outlined,
                label: 'Hot score',
                value: '${signals['hotScore'] ?? 0}',
                color: const Color(0xFFEF4444),
              ),
            ],
          ),
          const SizedBox(height: 14),
          Column(
            children: axes
                .map(
                  (axis) => Padding(
                    padding: const EdgeInsets.only(bottom: 10),
                    child: _buildAxisMeter(axis),
                  ),
                )
                .toList(),
          ),
        ],
      ),
    );
  }

  Widget _buildReaderRating(LangProvider lang) {
    return Container(
      padding: const EdgeInsets.all(18),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(16),
        boxShadow: [
          BoxShadow(color: Colors.black.withOpacity(0.05), blurRadius: 12),
        ],
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            lang.t('rating_title'),
            style: const TextStyle(fontSize: 16, fontWeight: FontWeight.bold),
          ),
          const SizedBox(height: 14),
          _buildStarRow(lang.t('innovation'), score1, (v) => score1 = v),
          _buildStarRow(lang.t('methodology'), score2, (v) => score2 = v),
          _buildStarRow(lang.t('evidence'), score3, (v) => score3 = v),
          _buildStarRow(lang.t('reproducibility'), score4, (v) => score4 = v),
          _buildStarRow(lang.t('impact_value'), score5, (v) => score5 = v),
          const SizedBox(height: 14),
          SizedBox(
            width: double.infinity,
            child: ElevatedButton(
              onPressed: _submitRating,
              style: ElevatedButton.styleFrom(
                backgroundColor: const Color(0xFFFFC107),
                foregroundColor: Colors.black,
              ),
              child: Text(lang.t('submit_rating')),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildStarRow(
    String label,
    double initialScore,
    ValueChanged<double> onUpdate,
  ) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 6),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        children: [
          Expanded(child: Text(label)),
          RatingBar.builder(
            initialRating: initialScore,
            minRating: 1,
            allowHalfRating: false,
            itemCount: 5,
            itemSize: 24,
            unratedColor: Colors.grey.shade300,
            itemBuilder: (_, __) =>
                const Icon(Icons.star_rounded, color: Colors.amber),
            onRatingUpdate: onUpdate,
          ),
        ],
      ),
    );
  }

  Widget _buildCommentComposer(LangProvider lang) {
    return Container(
      padding: const EdgeInsets.all(18),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(16),
        boxShadow: [
          BoxShadow(color: Colors.black.withOpacity(0.05), blurRadius: 12),
        ],
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            '${lang.t('tab_comment')} (${comments.length})',
            style: const TextStyle(fontSize: 16, fontWeight: FontWeight.bold),
          ),
          const SizedBox(height: 12),
          DropdownButtonFormField<String>(
            value: commentType,
            items: const [
              DropdownMenuItem(value: 'discussion', child: Text('Discussion')),
              DropdownMenuItem(value: 'question', child: Text('Question')),
              DropdownMenuItem(value: 'suggestion', child: Text('Suggestion')),
            ],
            onChanged: (value) {
              if (value == null) {
                return;
              }
              setState(() => commentType = value);
            },
            decoration: InputDecoration(
              filled: true,
              fillColor: const Color(0xFFF6F7F9),
              border: OutlineInputBorder(
                borderRadius: BorderRadius.circular(12),
                borderSide: BorderSide.none,
              ),
            ),
          ),
          const SizedBox(height: 12),
          TextField(
            controller: _commentCtrl,
            maxLines: 3,
            decoration: InputDecoration(
              hintText: lang.t('comment_hint'),
              filled: true,
              fillColor: const Color(0xFFF6F7F9),
              border: OutlineInputBorder(
                borderRadius: BorderRadius.circular(12),
                borderSide: BorderSide.none,
              ),
            ),
          ),
          const SizedBox(height: 12),
          Align(
            alignment: Alignment.centerRight,
            child: ElevatedButton(
              onPressed: _sendComment,
              style: ElevatedButton.styleFrom(
                backgroundColor: mainBlue,
                foregroundColor: Colors.white,
              ),
              child: Text(lang.t('send')),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildRelatedSection() {
    return Container(
      padding: const EdgeInsets.all(18),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(20),
        boxShadow: [
          BoxShadow(color: Colors.black.withOpacity(0.05), blurRadius: 12),
        ],
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Row(
            children: [
              Icon(Icons.explore_outlined, color: Color(0xFF2563EB)),
              SizedBox(width: 8),
              Text(
                'Related Research',
                style: TextStyle(fontSize: 16, fontWeight: FontWeight.bold),
              ),
            ],
          ),
          const SizedBox(height: 12),
          if (relatedTweets.isEmpty)
            Container(
              width: double.infinity,
              padding: const EdgeInsets.all(14),
              decoration: BoxDecoration(
                color: const Color(0xFFF8FAFC),
                borderRadius: BorderRadius.circular(14),
                border: Border.all(color: const Color(0xFFE2E8F0)),
              ),
              child: const Text(
                'There are not enough similar works yet. Recommendations will become richer as more research is shared.',
                style: TextStyle(
                  fontSize: 13,
                  height: 1.5,
                  color: Color(0xFF64748B),
                ),
              ),
            )
          else
            ...relatedTweets.take(6).map(_buildRelatedCard),
        ],
      ),
    );
  }

  Widget _buildRelatedCard(Map<String, dynamic> item) {
    final author = asMap(item['author']);
    final relatedId = asInt(item['id'], fallback: -1);
    final title = (item['title'] ?? item['content'] ?? 'Untitled Research Work')
        .toString()
        .trim();
    final summary = (item['content'] ?? '').toString().trim();
    final meta = [
      (item['researchArea'] ?? '').toString().trim(),
      (item['contentType'] ?? '').toString().trim(),
      (item['publicationType'] ?? '').toString().trim(),
    ].where((value) => value.isNotEmpty).toList();

    return Padding(
      padding: const EdgeInsets.only(bottom: 12),
      child: InkWell(
        borderRadius: BorderRadius.circular(16),
        onTap: relatedId <= 0 || relatedId == widget.tweetId
            ? null
            : () {
                Navigator.push(
                  context,
                  MaterialPageRoute(
                    builder: (_) => TweetDetailPage(tweetId: relatedId),
                  ),
                );
              },
        child: Container(
          width: double.infinity,
          padding: const EdgeInsets.all(16),
          decoration: BoxDecoration(
            gradient: const LinearGradient(
              begin: Alignment.topLeft,
              end: Alignment.bottomRight,
              colors: [Color(0xFFFBFDFF), Color(0xFFF5F9FF)],
            ),
            borderRadius: BorderRadius.circular(16),
            border: Border.all(color: const Color(0xFFDCEAFE)),
          ),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Container(
                padding: const EdgeInsets.symmetric(
                  horizontal: 10,
                  vertical: 5,
                ),
                decoration: BoxDecoration(
                  color: const Color(0xFFEAF4FF),
                  borderRadius: BorderRadius.circular(999),
                  border: Border.all(color: const Color(0xFFCFE1FF)),
                ),
                child: Text(
                  (item['contentType'] ?? 'Research Work').toString(),
                  style: const TextStyle(
                    fontSize: 11,
                    fontWeight: FontWeight.w700,
                    color: Color(0xFF2563EB),
                  ),
                ),
              ),
              const SizedBox(height: 10),
              Text(
                title,
                style: const TextStyle(
                  fontSize: 15,
                  fontWeight: FontWeight.w800,
                  color: Color(0xFF172554),
                ),
              ),
              const SizedBox(height: 8),
              Text(
                '${author['nickname'] ?? author['username'] ?? 'Researcher'}'
                '${(item['createTime'] ?? '').toString().isEmpty ? '' : ' - ${(item['createTime'] ?? '').toString().split('T')[0]}'}',
                style: const TextStyle(fontSize: 12, color: Color(0xFF64748B)),
              ),
              if (meta.isNotEmpty) ...[
                const SizedBox(height: 10),
                Wrap(
                  spacing: 8,
                  runSpacing: 8,
                  children: meta
                      .map(
                        (label) => Container(
                          padding: const EdgeInsets.symmetric(
                            horizontal: 10,
                            vertical: 5,
                          ),
                          decoration: BoxDecoration(
                            color: Colors.white,
                            borderRadius: BorderRadius.circular(999),
                            border: Border.all(color: const Color(0xFFE2E8F0)),
                          ),
                          child: Text(
                            label,
                            style: const TextStyle(
                              fontSize: 11,
                              fontWeight: FontWeight.w600,
                              color: Color(0xFF475569),
                            ),
                          ),
                        ),
                      )
                      .toList(),
                ),
              ],
              if (summary.isNotEmpty) ...[
                const SizedBox(height: 10),
                Text(
                  summary.length > 120
                      ? '${summary.substring(0, 120)}...'
                      : summary,
                  style: const TextStyle(
                    fontSize: 13,
                    height: 1.55,
                    color: Color(0xFF334155),
                  ),
                ),
              ],
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildCommentsList() {
    if (comments.isEmpty) {
      return Container(
        padding: const EdgeInsets.all(18),
        decoration: BoxDecoration(
          color: Colors.white,
          borderRadius: BorderRadius.circular(16),
        ),
        child: const Center(
          child: Text('No comments yet', style: TextStyle(color: Colors.grey)),
        ),
      );
    }

    final threads = _buildCommentThreads();

    return Column(
      children: threads.map((thread) {
        final parent = thread.parent;
        return Column(
          children: [
            _buildCommentCard(parent),
            ...thread.replies.map(
              (reply) => Padding(
                padding: const EdgeInsets.only(left: 28, top: 10, bottom: 2),
                child: _buildCommentCard(reply, isReply: true),
              ),
            ),
            const SizedBox(height: 12),
          ],
        );
      }).toList(),
    );
  }

  Widget _buildActionButton({
    required IconData icon,
    required String label,
    VoidCallback? onTap,
  }) {
    return OutlinedButton.icon(
      onPressed: onTap,
      icon: Icon(icon, size: 18),
      label: Text(label),
      style: OutlinedButton.styleFrom(
        foregroundColor: Colors.black87,
        side: const BorderSide(color: Color(0xFFD0D5DD)),
        padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
      ),
    );
  }

  Widget _buildMetricChip(IconData icon, String value) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
      decoration: BoxDecoration(
        color: const Color(0xFFF8FAFC),
        borderRadius: BorderRadius.circular(999),
        border: Border.all(color: const Color(0xFFE4E7EC)),
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

  Widget _buildTypeBadge(String type) {
    final config = _badgeConfig(type);

    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
      decoration: BoxDecoration(
        color: config.background,
        borderRadius: BorderRadius.circular(999),
        border: Border.all(color: config.border),
      ),
      child: Text(
        config.label,
        style: TextStyle(
          color: config.foreground,
          fontSize: 11,
          fontWeight: FontWeight.w600,
        ),
      ),
    );
  }

  Widget _buildCommentCard(
    Map<String, dynamic> comment, {
    bool isReply = false,
  }) {
    final user = asMap(comment['user']);
    final replyToUser = asMap(comment['replyToUser']);
    final commentId = asInt(comment['id'], fallback: -1);
    final isAccepted = comment['isAcceptedByAuthor'] == true;
    final isFeatured = comment['isFeatured'] == true;
    final isAuthorReply = comment['isAuthorReply'] == true;
    final canAccept = comment['canAccept'] == true;
    final isReplyBoxOpen = activeReplyCommentId == commentId;

    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: isReply ? const Color(0xFFF8FBFF) : Colors.white,
        borderRadius: BorderRadius.circular(20),
        border: Border.all(
          color: isAccepted
              ? const Color(0xFF86EFAC)
              : isFeatured
              ? const Color(0xFFCFE1FF)
              : const Color(0xFFE5EDF5),
        ),
        boxShadow: [
          BoxShadow(
            color: Colors.black.withOpacity(isReply ? 0.03 : 0.05),
            blurRadius: 12,
            offset: const Offset(0, 6),
          ),
        ],
      ),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          CircleAvatar(
            radius: isReply ? 18 : 20,
            backgroundImage: NetworkImage(buildAvatarUrl(user)),
          ),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Wrap(
                  spacing: 8,
                  runSpacing: 8,
                  crossAxisAlignment: WrapCrossAlignment.center,
                  children: [
                    Text(
                      (user['nickname'] ?? user['username'] ?? 'User')
                          .toString(),
                      style: const TextStyle(
                        fontWeight: FontWeight.bold,
                        fontSize: 14,
                      ),
                    ),
                    _buildTypeBadge(
                      (comment['commentType'] ?? 'discussion').toString(),
                    ),
                    if (isAccepted) _buildTypeBadge('accepted'),
                    if (isAuthorReply) _buildTypeBadge('author'),
                    if (comment['isTopScholar'] == true)
                      _buildTypeBadge('top_scholar'),
                    if (replyToUser.isNotEmpty) _buildTypeBadge('reply'),
                  ],
                ),
                const SizedBox(height: 8),
                if (replyToUser.isNotEmpty)
                  Padding(
                    padding: const EdgeInsets.only(bottom: 8),
                    child: Text(
                      'Replying to ${(replyToUser['nickname'] ?? replyToUser['username'] ?? 'user')}',
                      style: const TextStyle(
                        fontSize: 12,
                        color: Color(0xFF64748B),
                        fontWeight: FontWeight.w600,
                      ),
                    ),
                  ),
                if (isAccepted)
                  Container(
                    margin: const EdgeInsets.only(bottom: 10),
                    padding: const EdgeInsets.all(10),
                    decoration: BoxDecoration(
                      color: const Color(0xFFF0FDF4),
                      borderRadius: BorderRadius.circular(12),
                      border: Border.all(color: const Color(0xFFBBF7D0)),
                    ),
                    child: const Text(
                      'Accepted by the author as a valuable discussion.',
                      style: TextStyle(
                        color: Color(0xFF166534),
                        fontSize: 12,
                        height: 1.4,
                      ),
                    ),
                  ),
                Text(
                  (comment['content'] ?? '').toString(),
                  style: const TextStyle(fontSize: 14, height: 1.55),
                ),
                const SizedBox(height: 10),
                Wrap(
                  spacing: 10,
                  runSpacing: 8,
                  children: [
                    Text(
                      comment['createTime'].toString().split('T')[0],
                      style: TextStyle(
                        color: Colors.grey.shade500,
                        fontSize: 12,
                      ),
                    ),
                    _buildCommentAction(
                      icon: comment['isLiked'] == true
                          ? Icons.thumb_up
                          : Icons.thumb_up_alt_outlined,
                      label: '${comment['likeCount'] ?? 0}',
                      active: comment['isLiked'] == true,
                      onTap: () => _toggleCommentLike(comment),
                    ),
                    _buildCommentAction(
                      icon: Icons.reply_outlined,
                      label: 'Reply',
                      active: isReplyBoxOpen,
                      onTap: () {
                        if (commentId <= 0) {
                          return;
                        }
                        setState(() {
                          activeReplyCommentId = isReplyBoxOpen
                              ? null
                              : commentId;
                          _replyCtrl.clear();
                        });
                      },
                    ),
                    if (canAccept)
                      _buildCommentAction(
                        icon: isAccepted
                            ? Icons.check_circle
                            : Icons.check_circle_outline,
                        label: isAccepted ? 'Unaccept' : 'Accept',
                        active: isAccepted,
                        onTap: () => _toggleCommentAccepted(comment),
                      ),
                    if (comment['academicTitle'] != null)
                      _buildCommentMetaPill(
                        icon: Icons.school_outlined,
                        label: comment['academicTitle'].toString(),
                      ),
                    _buildCommentMetaPill(
                      icon: Icons.workspace_premium_outlined,
                      label: 'Rep ${comment['reputation'] ?? 0}',
                    ),
                  ],
                ),
                if (isReplyBoxOpen) ...[
                  const SizedBox(height: 12),
                  Row(
                    children: [
                      Expanded(
                        child: TextField(
                          controller: _replyCtrl,
                          minLines: 1,
                          maxLines: 3,
                          decoration: InputDecoration(
                            hintText: 'Reply to this discussion...',
                            filled: true,
                            fillColor: Colors.white,
                            border: OutlineInputBorder(
                              borderRadius: BorderRadius.circular(14),
                              borderSide: BorderSide.none,
                            ),
                          ),
                        ),
                      ),
                      const SizedBox(width: 8),
                      ElevatedButton(
                        onPressed: isReplySending
                            ? null
                            : () => _sendReply(comment),
                        style: ElevatedButton.styleFrom(
                          backgroundColor: mainBlue,
                          foregroundColor: Colors.white,
                          padding: const EdgeInsets.symmetric(
                            horizontal: 14,
                            vertical: 14,
                          ),
                        ),
                        child: const Text('Send'),
                      ),
                    ],
                  ),
                ],
              ],
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildCommentAction({
    required IconData icon,
    required String label,
    required VoidCallback onTap,
    bool active = false,
  }) {
    return InkWell(
      onTap: onTap,
      borderRadius: BorderRadius.circular(999),
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
        decoration: BoxDecoration(
          color: active ? const Color(0xFFE8F5FE) : const Color(0xFFF8FAFC),
          borderRadius: BorderRadius.circular(999),
          border: Border.all(
            color: active ? const Color(0xFFB9DFFF) : const Color(0xFFE2E8F0),
          ),
        ),
        child: Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(
              icon,
              size: 14,
              color: active ? mainBlue : Colors.grey.shade700,
            ),
            const SizedBox(width: 6),
            Text(
              label,
              style: TextStyle(
                fontSize: 12,
                fontWeight: FontWeight.w600,
                color: active ? mainBlue : Colors.grey.shade700,
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildCommentMetaPill({
    required IconData icon,
    required String label,
  }) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
      decoration: BoxDecoration(
        color: const Color(0xFFF8FAFC),
        borderRadius: BorderRadius.circular(999),
        border: Border.all(color: const Color(0xFFE2E8F0)),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(icon, size: 14, color: Colors.grey.shade700),
          const SizedBox(width: 6),
          Text(
            label,
            style: TextStyle(
              fontSize: 12,
              color: Colors.grey.shade700,
              fontWeight: FontWeight.w600,
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildDashboardStatCard({
    required IconData icon,
    required String label,
    required String value,
    required Color color,
  }) {
    return Container(
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(18),
        border: Border.all(color: const Color(0xFFE2ECF5)),
      ),
      child: Row(
        children: [
          Container(
            width: 36,
            height: 36,
            decoration: BoxDecoration(
              color: color.withOpacity(0.12),
              borderRadius: BorderRadius.circular(12),
            ),
            child: Icon(icon, color: color, size: 18),
          ),
          const SizedBox(width: 10),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  label,
                  style: const TextStyle(
                    fontSize: 11,
                    color: Color(0xFF64748B),
                    fontWeight: FontWeight.w600,
                  ),
                ),
                const SizedBox(height: 2),
                Text(
                  value,
                  style: const TextStyle(
                    fontSize: 18,
                    fontWeight: FontWeight.w800,
                    color: Color(0xFF0F172A),
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildAxisMeter(_AxisItem axis) {
    final progress = (axis.value / 5).clamp(0.0, 1.0);
    return Container(
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: Colors.white.withOpacity(0.82),
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: const Color(0xFFE2ECF5)),
      ),
      child: Column(
        children: [
          Row(
            children: [
              Text(
                axis.label,
                style: const TextStyle(
                  fontSize: 13,
                  fontWeight: FontWeight.w700,
                  color: Color(0xFF334155),
                ),
              ),
              const Spacer(),
              Text(
                axis.value.toStringAsFixed(1),
                style: const TextStyle(
                  fontSize: 13,
                  fontWeight: FontWeight.w800,
                  color: Color(0xFF0F172A),
                ),
              ),
            ],
          ),
          const SizedBox(height: 8),
          ClipRRect(
            borderRadius: BorderRadius.circular(999),
            child: LinearProgressIndicator(
              value: progress,
              minHeight: 8,
              backgroundColor: const Color(0xFFE5EEF7),
              valueColor: AlwaysStoppedAnimation<Color>(axis.color),
            ),
          ),
        ],
      ),
    );
  }

  List<_CommentThread> _buildCommentThreads() {
    final topLevel = <Map<String, dynamic>>[];
    final replyMap = <int, List<Map<String, dynamic>>>{};

    for (final comment in comments) {
      final parentId = asInt(comment['parentId'], fallback: -1);
      if (parentId <= 0) {
        topLevel.add(comment);
      } else {
        replyMap.putIfAbsent(parentId, () => []).add(comment);
      }
    }

    for (final replies in replyMap.values) {
      replies.sort((a, b) {
        final left = (a['createTime'] ?? '').toString();
        final right = (b['createTime'] ?? '').toString();
        return left.compareTo(right);
      });
    }

    return topLevel
        .map(
          (parent) => _CommentThread(
            parent: parent,
            replies:
                replyMap[asInt(parent['id'], fallback: -1)] ??
                <Map<String, dynamic>>[],
          ),
        )
        .toList();
  }

  List<_AxisItem> _axisItems() {
    return [
      _AxisItem('Innovation', _radarValue('s1'), const Color(0xFF0EA5E9)),
      _AxisItem('Method', _radarValue('s2'), const Color(0xFF6366F1)),
      _AxisItem('Evidence', _radarValue('s3'), const Color(0xFF14B8A6)),
      _AxisItem('Reproducibility', _radarValue('s4'), const Color(0xFFF59E0B)),
      _AxisItem('Impact', _radarValue('s5'), const Color(0xFFEC4899)),
    ];
  }

  _BadgeConfig _badgeConfig(String type) {
    switch (type) {
      case 'question':
        return const _BadgeConfig(
          'Question',
          Color(0xFFEEF4FF),
          Color(0xFFBFD7FF),
          Color(0xFF245D85),
        );
      case 'suggestion':
        return const _BadgeConfig(
          'Suggestion',
          Color(0xFFFFF7ED),
          Color(0xFFFED7AA),
          Color(0xFFB45309),
        );
      case 'accepted':
        return const _BadgeConfig(
          'Accepted',
          Color(0xFFF0FDF4),
          Color(0xFFBBF7D0),
          Color(0xFF166534),
        );
      case 'author':
        return const _BadgeConfig(
          'Author',
          Color(0xFFECFEFF),
          Color(0xFFA5F3FC),
          Color(0xFF0F766E),
        );
      case 'top_scholar':
        return const _BadgeConfig(
          'Top scholar',
          Color(0xFFFFFBEB),
          Color(0xFFFDE68A),
          Color(0xFF92400E),
        );
      case 'reply':
        return const _BadgeConfig(
          'Reply',
          Color(0xFFF8FAFC),
          Color(0xFFE2E8F0),
          Color(0xFF475569),
        );
      default:
        return const _BadgeConfig(
          'Discussion',
          Color(0xFFE8F5FE),
          Color(0xFFB9DFFF),
          Color(0xFF0369A1),
        );
    }
  }

  double _radarValue(String key) {
    final value = radar[key];
    if (value is num) {
      return value.toDouble();
    }
    return double.tryParse(value?.toString() ?? '') ?? 0;
  }

  double _toDouble(dynamic value) {
    if (value is num) {
      return value.toDouble();
    }
    return double.tryParse(value?.toString() ?? '') ?? 0;
  }
}

class _AxisItem {
  final String label;
  final double value;
  final Color color;

  const _AxisItem(this.label, this.value, this.color);
}

class _CommentThread {
  final Map<String, dynamic> parent;
  final List<Map<String, dynamic>> replies;

  const _CommentThread({required this.parent, required this.replies});
}

class _BadgeConfig {
  final String label;
  final Color background;
  final Color border;
  final Color foreground;

  const _BadgeConfig(this.label, this.background, this.border, this.foreground);
}
