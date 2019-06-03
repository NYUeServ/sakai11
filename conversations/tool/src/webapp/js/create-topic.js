Vue.component('create-topic-workflow', {
  template: `
  <div class="conversations-create-topic-workflow">
    <template v-if="step == 'SELECT_TYPE'">
      <div class="row">
        <div class="col-sm-12">
          <p class="text-center">Choose your topic type:</p>
        </div>
      </div>
      <div class="row">
        <div class="col-sm-3">
          <div class="conversations-topic">
            <p><strong>React</strong></p>
            <p><button class="button" v-on:click="selectTopicType('react')">Select Topic Type</button></p>
          </div>
        </div>
        <div class="col-sm-3">
          <div class="conversations-topic">
            <p><strong>Brainstorm</strong></p>
            <p>Coming soon.</p>
          </div>
        </div>
        <div class="col-sm-3">
          <div class="conversations-topic">
            <p><strong>Discuss</strong></p>
            <p>Coming soon.</p>
          </div>
        </div>
      </div>
    </template> 
    <template v-else-if="step == 'SET_TITLE'">
      <div class="row">
        <div class="col-sm-12">
          <i class="fa fa-arrow-left" aria-hidden="true"></i> <a href="#" v-on:click="step = 'SELECT_TYPE'">Back to select type</a>
        </div>
      </div>
      <div class="row">
        <div class="col-sm-6 col-sm-offset-3">
          <p class="text-center">
            <input class="form-control" placeholder="Topic title" v-model="topicTitle">
          </p>
        </div>
      </div>
      <div class="row">
        <div class="col-sm-12">
          <p class="text-center">
            <button class="button" v-on:click="selectTopicTitle()">Next <i class="fa fa-arrow-right" aria-hidden="true"></i></button>
          </p>
        </div>
      </div>
    </template>
    <template v-else-if="step == 'CREATE_FIRST_POST'">
      <div class="row">
        <div class="col-sm-12">
          <i class="fa fa-arrow-left" aria-hidden="true"></i> <a href="#" v-on:click="step = 'SET_TITLE'">Back to set title</a>
        </div>
      </div>
      <div class="row">
        <div class="col-sm-6 col-sm-offset-3">
          <p class="text-center">
            <div class="post-to-topic-textarea form-control">
              <div class="stretchy-editor" v-bind:class='{ "full-editor-height": editorFocused }'>
                <div class="topic-ckeditor"></div>
              </div>
            </div>
          </p>
        </div>
      </div>
      <div class="row">
        <div class="col-sm-12">
          <p class="text-center">
            <button class="button_color" v-on:click="createTopic()"><i class="fa fa-plus" aria-hidden="true"></i> Create Post</button>
          </p>
        </div>
      </div>
    </template>
  </div>
`,
  data: function() {
    return {
      step: 'SELECT_TYPE',
      topicType: null,
      topicTitle: '',
      editorFocused: false,
    };
  },
  props: ['baseurl'],
  methods: {
    firstPostContent: function() {
      if (this.editor) {
        return this.editor.getData();
      } else {
        return '';
      }
    },
    selectTopicType: function(type) {
      this.topicType = type;
      this.step = 'SET_TITLE';
    },
    selectTopicTitle: function() {
      if (this.topicTitle != '') {
        this.step = 'CREATE_FIRST_POST';
      }
    },
    createTopic: function() {
      if (this.firstPostContent() != '') {
        $.ajax({
          url: this.baseurl + 'create-topic',
          method: 'post',
          data: {
            title: this.topicTitle,
            type: this.topicType,
            post: this.firstPostContent(),
          },
          success: function() {
            location.reload();
          },
        });
      }
    },
    initRichTextareas: function() {
      $(this.$el).find('.topic-ckeditor').each((idx, elt) => {
        RichText.initialize({
          baseurl: this.baseurl,
          elt: elt,
          placeholder: 'Add initial topic post content...',
          onCreate: (newEditor) => {
            this.editor = newEditor;
          },
          onFocus: (event, name, isFocused) => {
            if (isFocused) {
              this.editorFocused = isFocused;
            }
          },
        });
      });
    },
  },
  updated: function() {
    this.initRichTextareas();
  },
});

Vue.component('create-topic-modal', {
  template: `
  <div class="conversations-create-topic-modal">
    <div class="modal" ref="dialog" role="dialog">
      <div class="modal-dialog" role="document">
        <div class="modal-content">
          <div class="modal-header">
            <button type="button" class="close" data-dismiss="modal" aria-label="Close">
              <span aria-hidden="true">&times;</span>
            </button>
            <div class="text-center">
              <span class="modal-title text-center">Add New Topic</span>
            </div>
          </div>
          <div class="modal-body">
            <create-topic-workflow :baseurl="baseurl"></create-topic-workflow>
          </div>
        </div>
      </div>
    </div>
  </div>
`,
  data: function() {
    return {};
  },
  props: ['baseurl'],
  methods: {
    show: function() {
      $(this.$refs.dialog).modal();
      this.resize();
    },
    resize: function() {
      const $dialog = $(this.$refs.dialog);
      if ($dialog.find('.modal-dialog').is(':visible')) {
        $dialog.find('.modal-dialog').width('95%');
        $dialog.find('.modal-content').height($(window).height() - 70);
      }
    },
  },
  mounted: function() {
    $(window).resize(() => {
      this.resize();
    });

    const $dialog = $(this.$refs.dialog);
    $dialog.on('shown.bs.modal', function() {
      $(document.body).css('overflow', 'hidden');
    }).on('hidden.bs.modal', function() {
      $(document.body).css('overflow', '');
    });
  },
});

Vue.component('create-topic-wrapper', {
  template: `
  <div class="conversations-create-topic-wrapper">
    <button class="button" v-on:click="showModal()"><i class="fa fa-plus" aria-hidden="true"></i> New Topic</button>
    <create-topic-modal ref="createTopicModal" :baseurl="baseurl"></create-topic-modal>
  </div>
`,
  data: function() {
    return {};
  },
  props: ['baseurl'],
  methods: {
    showModal: function() {
      this.$refs.createTopicModal.show();
    },
  },
  mounted: function() {
  },
});
