Vue.component('modal', {
  template: `
<div class="modal" tabindex="-1" role="dialog" ref="modal">
  <div class="modal-dialog" role="document">
    <div class="modal-content">
      <div class="modal-header">
        <div class="modal-title" style="float: left">
          <slot name="header"></slot>
        </div>
        <button type="button" class="close" data-dismiss="modal" aria-label="Close">
          <span aria-hidden="true">&times;</span>
        </button>
      </div>
      <div class="modal-body">
        <slot name="body"></slot>
      </div>
      <div class="modal-footer">
        <slot name="footer"></slot>
      </div>
    </div>
  </div>
</div>
  `,
  props: [],
  methods: {
    open: function() {
      $(this.$refs.modal).modal('show');
    },
    close: function() {
      $(this.$refs.modal).modal('hide');
    }
  },
  mounted: function() {
    var self = this;

    $(document).ready(function() {
      $(self.$refs.modal).modal({
        show: false,
      });
    });
  }
});

Vue.component('seat-assignment-widget', {
  template: `
    <span>
      <input type="text" v-model="seatValue" ref="input" :class="inputCSSClasses" />
      <button @click="save()">Save</button>
    </span>
  `,
  data: function() {
    return {
      hasError: false,
      seatValue: '', 
    };
  },
  props: ['assignment', 'meeting', 'group', 'section'],
  computed: {
      inputCSSClasses: function() {
        if (this.hasError) {
          return "has-error";
        } else {
          return ""
        }
      },
      baseurl: function() {
          return this.$parent.baseurl;
      },
      currentSeatAssignment: function() {
          // FIXME only clone once edit mode enabled
          // return clone of current seat value
          if (this.assignment.seat == null) {
              return '';
          } else {
              return '' + this.assignment.seat;
          }
      }
  },
  methods: {
    markHasError: function() {
      this.hasError = true;
    },
    clearHasError: function() {
      this.hasError = false;
    },
    save: function() {
      var self = this;
      self.clearHasError();

      $.ajax({
        url: this.baseurl + "/seat-assignment",
        type: 'post',
        dataType: 'json',
        data: {
          sectionId: self.section.id,
          groupId: self.group.id,
          meetingId: self.meeting.id,
          netid: self.assignment.netid,
          seat: self.seatValue,
          currentSeat: this.assignment.seat, // FIXME we want to grab this value at the point the instructor goes to edit it
        },
        success: function(json) {
          if (json.error) {
            self.markHasError();
            self.$nextTick(function() {
              self.$refs.input.select();
            });
          } else {
            location.reload();
          }
        }
      })
    }
  },
  mounted: function() {
    this.seatValue = this.currentSeatAssignment;
  },
});

Vue.component('split-action', {
  template: `
<div>
  <button @click="openModal()">Split</button>
  <modal ref="splitModal">
    <template v-slot:header>Split Section into Groups</template>
    <template v-slot:body>
      <div>
        <label for="numberofgroups">Number of Groups</label>
        <select id="numberofgroups" v-model="numberOfGroups">
          <option>1</option>
          <option>2</option>
          <option>3</option>
          <option>4</option>
        </select>
      </div>
      <div>
        <label for="selectionType">Selection Type</label>
        <select id="selectionType" v-model="selectionType">
          <option>RANDOM</option>
          <option>WEIGHTED</option>
        </select>
      </div>
    </template>
    <template v-slot:footer>
      <button @click="performSplit()" class="pull-left primary">Perform Split</button>
      <button @click="closeModal()">Cancel</button>
    </template>
  </modal>
</div>
`,
  props: ['section'],
  data: function() {
    return {
      numberOfGroups: 1,
      selectionType: 'RANDOM',
    }
  },
  computed: {
      baseurl: function() {
          return this.$parent.baseurl;
      },
  },
  methods: {
    openModal: function() {
      this.$refs.splitModal.open();
    },
    closeModal: function() {
      this.$refs.splitModal.close();
    },
    performSplit: function() {
      $.ajax({
        url: this.baseurl + "/split-section",
        type: 'post',
        data: {
          sectionId: this.section.id,
          numberOfGroups: this.numberOfGroups,
          selectionType: this.selectionType,
        },
        success: function() {
          location.reload();
        }
      })
    }
  }
});

Vue.component('group-meeting', {
  template: `
<div>
  <h3>{{meeting.name}} ({{meeting.id}})</h3>
  <table class="seat-assignment-listing">
    <thead>
      <tr>
        <th><span class="sr-only">Profile Picture</span></th>
        <th>NetID</th>
        <th>Modality</th>
        <th>Seat</th>
      </tr>
    </thead>
    <tbody>
      <tr v-for="assignment in sortedSeatAssignments">
        <td>
          <div class="profile-pic">
            <img :src="'/direct/profile/' + assignment.netid + '/image'"/>
          </div>
        </td>
        <td>{{assignment.netid}}</td>
        <td>
          <template v-if="assignment.official">In Roster</template>
          <template v-else>Unofficial</template>
        </td>
        <td>
          <seat-assignment-widget :assignment="assignment" :meeting="meeting" :group="group" :section="section">
          </seat-assignment-widget>
        </td>
      </tr>
    </tbody>
  </table>
</div>
`,
  props: ['section', 'group', 'meeting'],
  computed: {
    baseurl: function() {
        return this.$parent.baseurl;
    },
    sortedSeatAssignments: function() {
      return this.meeting.seatAssignments.sort(function(a, b) {
        return a.netid < b.netid;
      })
    },
  },
});

Vue.component('section-group', {
  template: `
<div>
  <h2>{{group.name}} ({{group.id}})</h2>
  <template v-for="meeting in group.meetings">
    <group-meeting :group="group" :section="section" :meeting="meeting"></group-meeting>
  </template>
</div>`,
  props: ['section', 'group'],
  computed: {
    baseurl: function() {
        return this.$parent.baseurl;
    },
  },
});

Vue.component('section-table', {
  template: `
    <div>
      <template v-if="section">
          <h1>{{section.id}}</h1>
          <split-action :section="section"></split-action>
          <template v-for="group in sortedGroups">
            <section-group :group="group" :section="section"></section-group>
          </template>
      </template>
    </div>
  `,

  data: function() {
    return {
        section: null,
    };
  },
  props: ['sectionId'],
  computed: {
      baseurl: function() {
          return this.$parent.baseurl;
      },
      sortedGroups: function() {
        if (this.section == null) {
          return [];
        } else {
          return this.section.groups.sort(function(a, b) {
            return a.name < b.name;
          })
        }
      },
  },
  methods: {
      fetchData: function() {
          var self = this;

          $.ajax({
              url: self.baseurl + 'section',
              data: {
                  sectionId: this.sectionId,
              },
              type: 'get',
              dataType: 'json',
              success: function (json) {
                  self.section = json;
              }
          });
      },
  },
  mounted: function() {
      this.fetchData();
  },
});


Vue.component('instructor-table', {
  template: `
      <div>
          <ul>
              <li v-for="s in sections">
                  {{s.name}} ({{s.id}})
              </li>
          </ul>
          <hr />
          <template v-for="s in sections">
              <section-table :sectionId="s.id"></section-table>
          </template>
      </div>

  `,
  data: function() {
    return {
        sections: [],
    };
  },
  props: ['baseurl'],
  methods: {
      fetchData: function() {
          var self = this;

          $.ajax({
              url: self.baseurl + 'sections',
              type: 'get',
              dataType: 'json',
              success: function (json) {
                  self.sections = json;
              }
          });
      },
  },
  mounted: function() {
      this.fetchData();
  },
});


Vue.component('react-topic', {
  template: `
  <div class="conversations-topic react">
    <div class="conversations-topic-main" ref="main">
        <template v-if="initialPost">
          <react-post :post="initialPost" initial_post="true"></react-post>
        </template>
        <post-editor ref="postEditor" :baseurl="baseurl">
          <template v-slot:author>
            <div class="conversations-postedby-photo">
              <img :src="'/direct/profile/'+ current_user_id + '/image'"/>
            </div>
          </template>
          <template v-slot:actions>
            <button class="button" v-on:click="markTopicRead(true)">
              Mark all as read
            </button>
          </template>
        </post-editor>
        <div class="conversations-posts">
          <template v-for="post in posts">
            <template v-if="post.isFirstUnreadPost">
              <div class="conversations-posts-unread-line">
                <span class="badge badge-primary">NEW</span>
              </div>
            </template>
            <react-post :topic_uuid="topic_uuid" :post="post"
                :baseurl="baseurl">
            </react-post>
          </template>
        </div>
    </div>
    <div class="conversations-topic-right">
        <template v-if="popupTimeline">
            <div :class="this.popupTimelinePopped ? 'conversations-timeline-toggle expanded' : 'conversations-timeline-toggle collapsed'" ref="timelineToggle">
              <a href="#" @click="togglePopupTimeline()">
                {{timelineDisplayString()}}
              </a>
              <div class="conversations-timeline-toggle-container">
                  <timeline :initialPost="initialPost" :posts="posts" ref="timeline"></timeline>
              </div>
            </div>
        </template>
        <template v-else>
            <timeline :initialPost="initialPost" :posts="posts"></timeline>
        </template>
        <topic-sidebar :current_user_role="current_user_role" :topic="topic" :posts="posts" :initial_post="initialPost"></topic_sidebar>
    </div>
  </div>
`,
  data: function() {
    return {
      posts: [],
      activeUploads: 0,
      initialPost: null,
      firstUnreadPost: null,
      postToFocusAndHighlight: null,
      topic: JSON.parse(this.topic_json),
      popupTimeline: false,
      popupTimelinePopped: false,
    };
  },
  props: [
    'baseurl',
    'topic_uuid',
    'topic_json',
    'settings_json',
    'current_user_id',
    'current_user_role'],
  methods: {
    refreshPosts: function(opts) {
      if (!opts) {
        opts = {};
      }

      this.firstUnreadPost = null;

      $.ajax({
        url: this.baseurl+'feed/posts',
        type: 'get',
        data: {topicUuid: this.topic_uuid},
        dataType: 'json',
        success: (json) => {
          if (json.length > 0) {
            this.initialPost = json.shift();
            this.posts = opts.fullRefresh ?
                json : this.mergePosts(json, this.posts);

            // FIXME IE support?
            const firstUnreadPost = this.posts.find(function(post) {
              return post.unread;
            });
            if (firstUnreadPost) {
              firstUnreadPost.isFirstUnreadPost = true;
            }
          } else {
            this.initialPost = null;
            this.posts = [];
          }
        },
      });
    },
    mergePosts: function(newPosts, origPosts) {
      // We want to preserve the unread statuses that were displayed at the
      // point the page loaded.
      const unreadStatuses = {};
      for (const post of origPosts) {
        unreadStatuses[post.uuid] = post.unread;
      }

      for (const post of newPosts) {
        if (unreadStatuses[post.uuid]) {
          post.unread = true;
        }
      }

      return newPosts;
    },
    formatEpochTime: function(epoch) {
      return new Date(epoch).toLocaleString();
    },
    markTopicRead: function(reloadPosts) {
      $.ajax({
        url: this.baseurl+'mark-topic-read',
        type: 'post',
        data: {topicUuid: this.topic_uuid},
        dataType: 'json',
        success: (json) => {
          if (reloadPosts) {
            this.refreshPosts({fullRefresh: true});
          }
        },
      });
    },
    resetMarkTopicReadEvents: function() {
      const markAsRead = () => {
        this.markTopicRead(false);
      };

      // If we're visible right now, mark as read immediately
      if (!document.hidden) {
        setTimeout(markAsRead, 0);
      }

      // Mark as read when the page unloads
      $(window).off('unload').on('unload', markAsRead);

      // Or when the tab becomes visible
      $(document).on('visibilitychange', () => {
        if (!document.hidden) {
          markAsRead();
        }
      });
    },
    focusAndHighlightPost: function(postUuid) {
      const $post = $(this.$el).find('[data-post-uuid='+postUuid+']');
      if ($post.length > 0) {
        $post[0].scrollIntoView({
          behavior: 'smooth',
          block: 'center',
        });
        $post.addClass('conversations-post-highlight');
        setTimeout(() => {
          $post.removeClass('conversations-post-highlight');
        }, 1000);
        return true;
      } else {
        return false;
      }
    },
    iconForMimeType: function(mimeType) {
      return this.$refs.postEditor.iconForMimeType(mimeType);
    },
    urlForAttachmentKey: function(key) {
      return this.$refs.postEditor.urlForAttachmentKey(key);
    },
    savePost: function(content, attachments) {
      $.ajax({
        url: this.baseurl+'create-post',
        type: 'post',
        data: {
          topicUuid: this.topic_uuid,
          content: content,
          attachmentKeys: attachments.map((attachment) => {
            return attachment.key;
          }),
        },
        dataType: 'json',
        success: (json) => {
          this.$refs.postEditor.clearEditor();
          this.postToFocusAndHighlight = json.uuid;
          this.refreshPosts();
        },
      });
    },
    handleResize: function() {
      if ($(window).width() < 1280) {
        if (this.popupTimeline === false) {
          this.popupTimelinePopped = false;
        }
        this.popupTimeline = true;
        this.$nextTick(() => {
          var collapsedHeight = $(this.$refs.timelineToggle).find('> a').outerHeight();
          $(this.$refs.timelineToggle).height(collapsedHeight);
        });
      } else {
        if (this.popupTimeline === true) {
          this.popupTimelinePopped = false;
        }
        this.popupTimeline = false;
      }
    },
    timelineDisplayString: function() {
      if (this.$refs.timeline) {
        return  this.$refs.timeline.popupDisplayString || '...' ;
      } else {
        return '...';
      }
    },
    togglePopupTimeline: function() {
      this.popupTimelinePopped = !this.popupTimelinePopped;
      if (this.popupTimelinePopped) {
        var expandedHeight = $(this.$refs.timelineToggle).find('.conversations-timeline').height() + $(this.$refs.timelineToggle).find('> a').outerHeight();
        $(this.$refs.timelineToggle).height(expandedHeight);
      } else {
        var collapsedHeight = $(this.$refs.timelineToggle).find('> a').outerHeight();
        $(this.$refs.timelineToggle).height(collapsedHeight);
      }
    },
  },
  computed: {
    settings: function() {
      return this.topic.settings;
    },
    topic_title: function() {
      return this.topic.title;
    },
    allowComments: function() {
        return this.settings.allow_comments;
    },
    allowLikes: function() {
        return this.settings.allow_like;
    },
  },
  mounted: function() {
    this.refreshPosts();
    setInterval(() => {
      this.refreshPosts();
    }, 5*1000);
    this.resetMarkTopicReadEvents();
    $(window).on('resize', () => {
      this.handleResize();
    });
    this.handleResize();
  },
  updated: function() {
    // If we added a new rich text area, enrich it!
    this.$nextTick(() => {
      if (this.postToFocusAndHighlight) {
        if (this.focusAndHighlightPost(this.postToFocusAndHighlight)) {
          this.postToFocusAndHighlight = null;
        }
      }
    });
  },
});


Vue.component('post-editor', {
  template: `
<div class="conversations-post-form">
  <slot name="author"></slot>
  <div class="post-to-topic-textarea form-control">
    <div class="stretchy-editor"
        v-bind:class='{ "full-editor-height": editorFocused }'>
      <div class="topic-ckeditor"><slot name="content"></slot></div>
    </div>
    <div>
      <hr>
      <button v-on:click="newAttachment()" class="conversations-minimal-btn">
        <i class="fa fa-paperclip"></i>&nbsp;Add attachment
      </button>
      <ul class="conversations-attachment-list">
        <li v-for="a in attachments">
          <i class="fa" v-bind:class='a.icon'></i>
          &nbsp;
          <a :href='a.url'>{{a.name}}</a>
        </li>
      </ul>
    </div>
  </div>
  <template v-if="activeUploads === 0">
    <button class="button" v-on:click="savePost()">Post</button>
  </template>
  <template v-else>
    <button class="button" disabled>Uploading...</button>
  </template>
  <slot name="actions"></slot>
</div>
`,
  data: function() {
    const mimeToIconMap = {
      'application/pdf': 'fa-file-pdf-o',
      'text/pdf': 'fa-file-pdf-o',

      'application/msword': 'fa-file-word-o',
      'application/vnd.openxmlformats-officedocument.wordprocessingml.document': 'fa-file-word-o',
      'application/vnd.openxmlformats-officedocument.wordprocessingml.template': 'fa-file-word-o',

      'application/vnd.ms-powerpoint': 'fa-file-powerpoint-o',
      'application/vnd.openxmlformats-officedocument.presentationml.presentation': 'fa-file-powerpoint-o',
      'application/vnd.openxmlformats-officedocument.presentationml.template': 'fa-file-powerpoint-o',
      'application/vnd.openxmlformats-officedocument.presentationml.slideshow': 'fa-file-powerpoint-o',

      'application/vnd.ms-excel': 'fa-file-excel-o',
      'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet': 'fa-file-excel-o',
      'application/vnd.openxmlformats-officedocument.spreadsheetml.template': 'fa-file-excel-o',

      'image/jpeg': 'fa-file-image-o',
      'image/png': 'fa-file-image-o',
      'image/gif': 'fa-file-image-o',
      'image/tiff': 'fa-file-image-o',
      'image/bmp': 'fa-file-image-o',

      'application/zip': 'fa-file-archive-o',
      'application/x-rar-compressed': 'fa-file-archive-o',

      'text/plain': 'fa-file-text-o',

      'video/mp4': 'fa-file-video-o',
      'video/x-flv': 'fa-file-video-o',
      'video/quicktime': 'fa-file-video-o',
      'video/mpeg': 'fa-file-video-o',
      'video/ogg': 'fa-file-video-o',

      'audio/mpeg': 'fa-file-audio-o',
      'audio/ogg': 'fa-file-audio-o',
      'audio/midi': 'fa-file-audio-o',
      'audio/flac': 'fa-file-audio-o',
      'audio/aac': 'fa-file-audio-o',
    };

    const existingAttachments = [];

    if (this.existing_attachments) {
      this.existing_attachments.forEach((attachment) => {
        existingAttachments.push({
          name: attachment.fileName,
          icon: mimeToIconMap[attachment.mimeType] || 'fa-file',
          key: attachment.key,
          url: this.urlForAttachmentKey(attachment.key),
        });
      });
    }

    return {
      editorFocused: false,
      attachments: existingAttachments,
      activeUploads: 0,
      editor: null,
      mimeToIcon: mimeToIconMap,
    };
  },
  computed: {
    topic_uuid: function() {
      return this.$parent.topic_uuid;
    },
  },
  props: ['existing_attachments', 'baseurl'],
  methods: {
    initRichTextareas: function() {
      $(this.$el).find('.topic-ckeditor').each((idx, elt) => {
        RichText.initialize({
          baseurl: this.baseurl,
          elt: elt,
          placeholder: 'React to the post...',
          onCreate: (newEditor) => {
            this.editor = newEditor;
          },
          onUploadEvent: (status) => {
            if (status === 'started') {
              this.activeUploads += 1;
            } else {
              this.activeUploads -= 1;
            }
          },
          onFocus: (event, name, isFocused) => {
            if (isFocused) {
              this.editorFocused = isFocused;
            } else {
              if (this.editor.getData() === '') {
                this.editorFocused = false;
              }
            }
          },
        });
      });
    },
    iconForMimeType: function(mimeType) {
      return this.mimeToIcon[mimeType] || 'fa-file';
    },
    urlForAttachmentKey: function(key) {
      return this.baseurl + 'file-view?mode=view&key=' + key;
    },
    newAttachment: function() {
      const self = this;
      const fileInput = $('<input type="file" style="display: none;"></input>');

      $(this.$el).append(fileInput);

      fileInput.click();

      fileInput.on('change', function() {
        const file = fileInput[0].files[0];
        const formData = new FormData();
        formData.append('file', file);
        formData.append('mode', 'attachment');

        self.activeUploads += 1;

        $.ajax({
          url: self.baseurl + 'file-upload',
          type: 'POST',
          contentType: false,
          cache: false,
          processData: false,
          data: formData,
          dataType: 'json',
          success: function(response) {
            self.attachments.push({
              name: file.name,
              icon: self.iconForMimeType(file.type),
              key: response.key,
              url: self.urlForAttachmentKey(response.key),
            });
          },
          error: function(xhr, statusText) {},
          complete: function() {
            self.activeUploads -= 1;
          },
        });
      });
    },
    clearEditor: function() {
      if (this.editor) {
        this.attachments = [];
        this.editor.setData('');
        this.editorFocused = false;
      }
    },
    newPostContent: function() {
      if (this.editor) {
        return this.editor.getData();
      } else {
        return '';
      }
    },
    savePost: function() {
      let content = this.newPostContent().trim();

      if (content === '') {
        if (this.attachments.length === 0) {
          this.clearEditor();
          return;
        } else {
          // Blank content is OK if we have attachments.  Store a placeholder.
          content = '&nbsp;';
        }
      }

      this.$parent.savePost(content, this.attachments);
    },
  },
  mounted: function() {
    this.initRichTextareas();
  },
  updated: function() {},
});
