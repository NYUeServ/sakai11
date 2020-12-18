window.BrightspaceMigrator = function() {
}

BrightspaceMigrator.prototype.init = function() {
  this.on = false;

  var $menuItem = $('<li>').addClass('Mrphs-userNav__submenuitem').addClass('Mrphs-userNav__submenuitem-indented');
  this.$button = $('<a>')
        .attr('href', 'javascript:void(0);')
        .addClass('Mrphs-userNav__submenuitem--migrator')
        .attr('role', 'menuitem')
        .html('Migrate to Brightspace');

  $menuItem.append(this.$button);

  this.$button.on('click', $.proxy(this.handleClick, this));

  $('#mastLogin .Mrphs-userNav__subnav .Mrphs-userNav__logout').before($menuItem);

  this.currentPage = 0;
}

BrightspaceMigrator.prototype.handleClick = function() {
  var self = this;

  if (self.on) {
    return;
  }

  self.on = true;

  self.showDialog();
}

BrightspaceMigrator.prototype.refreshData = function(callback) {
  var self = this;
  if (self.on) {
    $.ajax({
      cache: false,
      url: '/portal/brightspace-migrator',
      data: {
        'term': self.termFilter || '',
        'q': self.queryFilter || '',
        'page': self.currentPage,
      },
      dataType: 'json',
      method: 'GET',
      success: function(json) {
        $('#nyuBrightspaceMigratorModal tbody').empty();
        self._data = json;
        if (self._data.sites.length === 0) {
          if (self.queryFilter || self.termFilter) {
            $('#nyuBrightspaceMigratorModal tbody').append($('<tr><td colspan="3">Sorry, no sites matching filter</td></tr>'));
          } else {
            $('#nyuBrightspaceMigratorModal tbody').append($('<tr><td colspan="3">Sorry, there are no sites available for export</td></tr>'));
          }
        } else {
          var $targetTbody = $('#nyuBrightspaceMigratorModal tbody');
          self._data.sites.forEach(function(site) {
            var $tr = $('<tr>');
            $tr.data('site', site);
            var $siteTitleCell = $('<td>');
            $siteTitleCell.append($('<a>').text(site.title).attr('href', '/portal/site/'+site.site_id).attr('target', '_blank'));
            if (site.instructors && site.instructors.length > 0) {
              var $instructorsList = $('<ul>');
              for (var i=0; i<site.instructors.length; i++) {
                var instructor = site.instructors[i];
                $instructorsList.append($('<li>').text(instructor.display));
              }
              $siteTitleCell.append($('<div>').text('Instructors:'));
              $siteTitleCell.append($instructorsList);
            }
            if (site.rosters && site.rosters.length > 0) {
              var $rosterList = $('<ul>');
              for (var i=0; i<Math.min(site.rosters.length, 5); i++) {
                $rosterList.append($('<li>').text(site.rosters[i]));
              }
              if (site.rosters.length > 5) {
                $rosterList.append($('<li>').text('...'));
              }
              $siteTitleCell.append($('<div>').text('Rosters:'));
              $siteTitleCell.append($rosterList);
            }
            $tr.append($siteTitleCell);
            $tr.append($('<td>').text(site.term.replace('_', ' ')));
            if (site.queued) {
              var $state = $($('#nyuBrightspaceMigratorStateTemplate').html());
              if (site.queued_at > 0) {
                var tooltip = "Queued at " + new Date(site.queued_at).toLocaleString() + " by " + site.queued_by;
                $($state.find('.progress-bar')[0]).attr('title', tooltip);
              }
              if (site.archived_at > 0) {
                var tooltip = "Archived at " + new Date(site.archived_at).toLocaleString();
                var $progressBar = $($state.find('.progress-bar')[1]);
                $progressBar.attr('title', tooltip);
                $progressBar.removeClass('inactive');
                $progressBar.attr('aria-valuenow', '1');
              }
              if (site.uploaded_at > 0) {
                var tooltip = "Uploaded to Brightspace at " + new Date(site.uploaded_at).toLocaleString();
                var $progressBar = $($state.find('.progress-bar')[2]);
                $progressBar.attr('title', tooltip);
                $progressBar.removeClass('inactive');
                $progressBar.attr('aria-valuenow', '1');
              }
              if (site.completed_at > 0) {
                var tooltip = "Successfully imported into Brightspace at " + new Date(site.completed_at).toLocaleString();
                var $progressBar = $($state.find('.progress-bar')[3]);
                $progressBar.attr('title', tooltip);
                $progressBar.removeClass('inactive');
                $progressBar.attr('aria-valuenow', '1');
              }
              $state.find('.brightspace-migrator-state-' + site.status.toLowerCase()).addClass('progress-bar-success');
              var $lastTd = $('<td>');
              $lastTd.append($state);
              $tr.append($lastTd);
              $state.css('display', 'block');

              if (site.brightspace_org_unit_id > 0) {
                $lastTd.append(
                  $('<a>')
                    .attr('href', 'https://brightspace.nyu.edu/d2l/home/'+site.brightspace_org_unit_id)
                    .attr('target','_blank')
                    .addClass('button')
                    .addClass('pull-right')
                    .html('View in Brightspace'))
              }
            } else {
              $tr.append($('<td>').html($('<button class="btn-primary nyu-trigger-brightspace-migration">Queue for Export</button>')));
            }
            $targetTbody.append($tr);
          });
        }

        if (self._data.has_previous_page || self._data.has_next_page) {
          $('#nyuBrightspaceMigratorModal .brightspace-migrator-pagination').show();
          $('#nyuBrightspaceMigratorModal .brightspace-migrator-previous').prop('disabled', !self._data.has_previous_page);
          $('#nyuBrightspaceMigratorModal .brightspace-migrator-next').prop('disabled', !self._data.has_next_page);
        } else {
          $('#nyuBrightspaceMigratorModal .brightspace-migrator-pagination').hide();
        }

        $('#nyuBrightspaceMigratorModal .brightspace-migrator-filter-term').empty();
        $('#nyuBrightspaceMigratorModal .brightspace-migrator-filter-term').append($('<option>'));
        self._data.terms.forEach(function(term) {
          var $option = $('<option>').val(term).text(term.replace('_', ' '));
          $('#nyuBrightspaceMigratorModal .brightspace-migrator-filter-term').append($option);
        });
        if (self.termFilter) {
          $('#nyuBrightspaceMigratorModal .brightspace-migrator-filter-term').val(self.termFilter);
        }
        $('#nyuBrightspaceMigratorModal .brightspace-migrator-filter-term').on('change', function() {
          if (self.termFilter !== $(this).val()) {
            self.termFilter = $(this).val();
            self.currentPage = 0;
            self.refreshData();
          }
        });
        var typingDelay;
        $('#nyuBrightspaceMigratorModal .brightspace-migrator-filter-query').val(self.queryFilter);
        $('#nyuBrightspaceMigratorModal .brightspace-migrator-filter-query').on('keyup', function() {
          if (typingDelay) {
            clearTimeout(typingDelay);
          }

          var $input = $(this);
          typingDelay = setTimeout(function() {
            if (self.queryFilter !== $input.val()) {
              self.queryFilter = $input.val();
              self.currentPage = 0;
              self.refreshData();
            }
          }, 500);
        });
        if ((self.queryFilter || '') !== '' || (self.termFilter || '') !== '') {
          $('#nyuBrightspaceMigratorModal .brightspace-migrator-filter-clear').show();
        } else {
          $('#nyuBrightspaceMigratorModal .brightspace-migrator-filter-clear').hide();
        }
        $('#nyuBrightspaceMigratorModal .brightspace-migrator-filter-clear').on('click', function() {
          self.queryFilter = '';
          self.termFilter = '';
          self.currentPage = 0;
          self.refreshData();
        });

        $(window).off('resize', self.handleResize).on('resize', self.handleResize);

        if (callback) {
          callback();
        }
      }
    });
  }
}

BrightspaceMigrator.prototype.handleResize = function() {
  $('#nyuBrightspaceMigratorModal .modal-dialog .modal-body').height(Math.max(300, $(window).height() - 250));
}

BrightspaceMigrator.prototype.showDialog = function() {
  var self = this;
  $(document.body).append($("#nyuBrightspaceMigratorModalTemplate").html());
  $('#nyuBrightspaceMigratorModal')
    .on('hidden.bs.modal', function() {
      self.on = false;
      $('#nyuBrightspaceMigratorModal').remove();
    })
    .on('shown.bs.modal', function() {
      self.handleResize();
      self.refreshData();
    })
    .on('click', '.nyu-trigger-brightspace-migration', function(event) {
      event.preventDefault();

      $(event.target).prop('disabled', true);

      var $tr = $(event.target).closest('tr');
      var siteData = $tr.data('site');

      $.post('/portal/brightspace-migrator', {
          site_id: siteData.site_id,
      }, function() {
          self.refreshData();
      });
    })
    .on('click', '.brightspace-migrator-previous', function(event) {
      event.preventDefault();
      self.currentPage = self.currentPage - 1;
      self.refreshData(function() {
        $('#nyuBrightspaceMigratorModal .modal-body')[0].scrollTop = 0;
      });
    })
    .on('click', '.brightspace-migrator-next', function(event) {
      event.preventDefault();
      self.currentPage = self.currentPage + 1;
      self.refreshData(function() {
        $('#nyuBrightspaceMigratorModal .modal-body')[0].scrollTop = 0;
      });
    });
  $('#nyuBrightspaceMigratorModal').modal();
}

new BrightspaceMigrator().init();
