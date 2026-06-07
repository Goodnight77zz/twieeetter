import 'dart:async';

import 'package:flutter/material.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'api_config.dart';
import 'api_helpers.dart';

class AppText {
  final Map<String, String> _tagMap = {
    'AI': 'AI',
    'Biomedical': 'Biomedical',
    'Computer Science': 'Computer Science',
    'Physics': 'Physics',
    'Chemistry': 'Chemistry',
    'Deep Learning': 'Deep Learning',
    'Data Analysis': 'Data Analysis',
    'Unclassified': 'Unclassified',
  };

  final Map<String, String> _cn = {
    'app_name': 'Open Academic Platform',
    'loading': 'Loading...',
    'network_error': 'Network error',
    'attachment': 'Attachment',
    'search_hint': '搜索学术内容...',
    'search_page_hint': '搜索用户、标题、关键词...',
    'search_btn': '搜索',
    'search_empty': 'No results found',
    'cancel': 'Cancel',
    'share': 'Share',
    'comment': 'Comment',
    'send': 'Send',
    'please_input': 'Please complete required fields',
    'save_success': 'Saved successfully',
    'tab_home': 'Home',
    'tab_publish': 'Publish',
    'tab_mine': 'Me',
    'tab_search_tweets': 'Works',
    'tab_search_users': 'Users',
    'tab_intro': 'Overview',
    'tab_comment': 'Comments',
    'tab_notify_reply': '回复',
    'tab_notify_like': '点赞',
    'tab_notify_fan': '其他',
    'notifications_title': '通知',
    'mark_all_read': '全部已读',
    'commented_your_post': '评论了你的成果',
    'replied_your_comment': '回复了你的评论',
    'liked_your_post': '点赞了你的成果',
    'liked_your_comment': '点赞了你的评论',
    'subscription_update': '你订阅的领域有新成果',
    'accepted_your_comment': '你的评论被作者采纳',
    'followed_you': '关注了你',
    'no_notifications': '暂无通知',
    'login_title': 'Login',
    'account_hint': 'Username',
    'password_hint': 'Password',
    'login_btn': 'Login',
    'processing': 'Processing...',
    'go_register': 'No account? Register',
    'login_success': 'Login successful',
    'login_failed': 'Invalid username or password',
    'register_title': 'Create account',
    'email_hint': 'Email',
    'confirm_password_hint': 'Confirm password',
    'register_btn': 'Register',
    'go_login': 'Already have an account? Login',
    'password_mismatch': 'Passwords do not match',
    'register_success': 'Registration successful, please login',
    'title_student': 'Research Participant',
    'title_lecturer': 'Lecturer',
    'title_professor': 'Professor',
    'creator_center': 'Creator Center',
    'go_publish': 'Publish',
    'more_services': 'Services',
    'score_label': 'Academic Reputation',
    'logout': 'Logout',
    'label_following': 'Following',
    'label_follower': 'Followers',
    'menu_edit_profile': 'Edit Profile',
    'menu_manuscripts': 'My Works',
    'menu_settings': 'Settings',
    'edit_profile_title': 'Edit Profile',
    'edit_nickname': 'Nickname',
    'edit_bio': 'Bio',
    'save_changes': 'Save Changes',
    'change_avatar': 'Tap to change avatar',
    'publish_title': 'Publish Research Work',
    'publish_success': 'Published successfully',
    'publish_error': 'Publishing failed',
    'publish_now': 'Publish Now',
    'content_hint': 'Enter abstract or research description',
    'upload_area': 'Upload attachment (PDF / Word)',
    'upload_sub_hint': 'Tap to choose a file',
    'file_selected': 'Selected: ',
    'tags_label': 'Tags',
    'my_content_title': 'My Works',
    'filter_tag': 'Filter by tag',
    'all_tags': 'All tags',
    'no_content': 'No content yet',
    'view_details': 'View details',
    'detail_title': 'Work Details',
    'ai_review': 'AI Review',
    'ai_btn_start': 'Analyze',
    'ai_btn_analyzing': 'Analyzing...',
    'ai_placeholder': 'Tap the button and AI will analyze the attachment.',
    'rating_title': 'My Rating',
    'submit_rating': 'Submit Rating',
    'innovation': 'Innovation',
    'methodology': 'Method Rigor',
    'utility': 'Practical Value',
    'evidence': 'Evidence Strength',
    'reproducibility': 'Reproducibility',
    'impact_value': 'Dissemination Value',
    'downloads_count': 'Downloads',
    'expert_rating': 'Expert Rating',
    'expert_count': 'Experts',
    'impact_dashboard': 'Impact Dashboard',
    'your_work': 'This is your work',
    'download_file': 'Download Attachment',
    'comment_hint': 'Write your comment...',
    'login_expired': 'Session expired, please login again',
    'rating_success': 'Rating submitted',
    'btn_follow': '+ Follow',
    'btn_followed': 'Following',
  };

  final Map<String, String> _en = {
    'app_name': 'Open Academic Platform',
    'loading': 'Loading...',
    'network_error': 'Network error',
    'attachment': 'Attachment',
    'search_hint': 'Search academic content...',
    'search_page_hint': 'Search users, titles, keywords...',
    'search_btn': 'Search',
    'search_empty': 'No results found',
    'cancel': 'Cancel',
    'share': 'Share',
    'comment': 'Comment',
    'send': 'Send',
    'please_input': 'Please complete required fields',
    'save_success': 'Saved successfully',
    'tab_home': 'Home',
    'tab_publish': 'Publish',
    'tab_mine': 'Me',
    'tab_search_tweets': 'Works',
    'tab_search_users': 'Users',
    'tab_intro': 'Overview',
    'tab_comment': 'Comments',
    'tab_notify_reply': 'Replies',
    'tab_notify_like': 'Likes',
    'tab_notify_fan': 'Others',
    'notifications_title': 'Notifications',
    'mark_all_read': 'Mark all read',
    'commented_your_post': 'commented on your work',
    'replied_your_comment': 'replied to your comment',
    'liked_your_post': 'liked your work',
    'liked_your_comment': 'liked your comment',
    'subscription_update': 'a subscribed area has a new work',
    'accepted_your_comment': 'your comment was accepted by the author',
    'followed_you': 'followed you',
    'no_notifications': 'No notifications yet',
    'login_title': 'Login',
    'account_hint': 'Username',
    'password_hint': 'Password',
    'login_btn': 'Login',
    'processing': 'Processing...',
    'go_register': 'No account? Register',
    'login_success': 'Login successful',
    'login_failed': 'Invalid username or password',
    'register_title': 'Create account',
    'email_hint': 'Email',
    'confirm_password_hint': 'Confirm password',
    'register_btn': 'Register',
    'go_login': 'Already have an account? Login',
    'password_mismatch': 'Passwords do not match',
    'register_success': 'Registration successful, please login',
    'title_student': 'Research Participant',
    'title_lecturer': 'Lecturer',
    'title_professor': 'Professor',
    'creator_center': 'Creator Center',
    'go_publish': 'Publish',
    'more_services': 'Services',
    'score_label': 'Academic Reputation',
    'logout': 'Logout',
    'label_following': 'Following',
    'label_follower': 'Followers',
    'menu_edit_profile': 'Edit Profile',
    'menu_manuscripts': 'My Works',
    'menu_settings': 'Settings',
    'edit_profile_title': 'Edit Profile',
    'edit_nickname': 'Nickname',
    'edit_bio': 'Bio',
    'save_changes': 'Save Changes',
    'change_avatar': 'Tap to change avatar',
    'publish_title': 'Publish Research Work',
    'publish_success': 'Published successfully',
    'publish_error': 'Publishing failed',
    'publish_now': 'Publish Now',
    'content_hint': 'Enter abstract or research description',
    'upload_area': 'Upload attachment (PDF / Word)',
    'upload_sub_hint': 'Tap to choose a file',
    'file_selected': 'Selected: ',
    'tags_label': 'Tags',
    'my_content_title': 'My Works',
    'filter_tag': 'Filter by tag',
    'all_tags': 'All tags',
    'no_content': 'No content yet',
    'view_details': 'View details',
    'detail_title': 'Work Details',
    'ai_review': 'AI Review',
    'ai_btn_start': 'Analyze',
    'ai_btn_analyzing': 'Analyzing...',
    'ai_placeholder': 'Tap the button and AI will analyze the attachment.',
    'rating_title': 'My Rating',
    'submit_rating': 'Submit Rating',
    'innovation': 'Innovation',
    'methodology': 'Method Rigor',
    'utility': 'Practical Value',
    'evidence': 'Evidence Strength',
    'reproducibility': 'Reproducibility',
    'impact_value': 'Dissemination Value',
    'downloads_count': 'Downloads',
    'expert_rating': 'Expert Rating',
    'expert_count': 'Experts',
    'impact_dashboard': 'Impact Dashboard',
    'your_work': 'This is your work',
    'download_file': 'Download Attachment',
    'comment_hint': 'Write your comment...',
    'login_expired': 'Session expired, please login again',
    'rating_success': 'Rating submitted',
    'btn_follow': '+ Follow',
    'btn_followed': 'Following',
  };

  String getTag(String rawTag, String langCode) {
    if (langCode == 'zh') {
      return rawTag;
    }
    return _tagMap[rawTag] ?? rawTag;
  }

  String get(String key, String langCode) {
    if (langCode == 'en') {
      return _en[key] ?? key;
    }
    return _cn[key] ?? key;
  }
}

class LangProvider extends ChangeNotifier {
  String _currentLang = 'zh';
  final AppText _textData = AppText();
  int _unreadCount = 0;
  Timer? _timer;

  String get currentLang => _currentLang;
  int get unreadCount => _unreadCount;

  LangProvider() {
    _loadFromPrefs();
    _startPollingUnread();
  }

  @override
  void dispose() {
    _timer?.cancel();
    super.dispose();
  }

  void _startPollingUnread() {
    _timer = Timer.periodic(const Duration(seconds: 10), (_) {
      refreshUnreadCount();
    });
  }

  Future<void> refreshUnreadCount() async {
    try {
      final prefs = await SharedPreferences.getInstance();
      final userId = prefs.getInt('userId') ?? 0;
      if (userId == 0) {
        if (_unreadCount != 0) {
          _unreadCount = 0;
          notifyListeners();
        }
        return;
      }

      final res = await createSessionDio().get(
        '$baseUrl/notifications/unread-count',
        queryParameters: {'userId': userId},
      );

      final payload = res.data;
      final nextCount = payload is num
          ? payload.toInt()
          : asInt(asMap(payload)['count']);
      if (nextCount != _unreadCount) {
        _unreadCount = nextCount;
        notifyListeners();
      }
    } catch (_) {}
  }

  String tTag(String rawTag) {
    return _textData.getTag(rawTag, _currentLang);
  }

  String t(String key) {
    return _textData.get(key, _currentLang);
  }

  Future<void> switchLang() async {
    _currentLang = _currentLang == 'zh' ? 'en' : 'zh';
    notifyListeners();

    final prefs = await SharedPreferences.getInstance();
    await prefs.setString('app_lang', _currentLang);
  }

  Future<void> _loadFromPrefs() async {
    final prefs = await SharedPreferences.getInstance();
    _currentLang = prefs.getString('app_lang') ?? 'zh';
    notifyListeners();
    await refreshUnreadCount();
  }
}
