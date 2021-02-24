Vue.prototype.SeatToolMode = 'sakai'

SeatToolEventBus = new Vue();

StudentLocationLabels = {
  'IN_PERSON': 'In-Person',
  'REMOTE': 'Remote',
};

StudentLocationSortOrder = {
  'IN_PERSON': 0,
  'REMOTE': 1,
};

Alerts = {
  messages: {
    "EDIT_CLOSED": "The edit period has closed for this seat. Please contact your instructor.",
    "SEAT_TAKEN": "Someone is already in the seat you have selected. Please check your seat number and contact your instructor if necessary.",
    "SEAT_TAKEN_INSTRUCTOR": "The seat number you entered is already in use. Please enter a different seat number.",
    "CONCURRENT_UPDATE": "This seat has been updated since you started editing. Please check the new value and retry if needed.",
    "SAVE_SUCCESS": "Seat successfully updated.",
    "EMAIL_SENT": "Your email has been sent.",
    "SEAT_REQUIRED": "A seat value is required.",
    "MEMBER_ADDED": "Additional site member(s) successfully added.",
  }
}

Alerts.clear = function() {
  $('.growl-close').click();
};

Alerts.success = function (code) {
  Alerts.clear();

  $.growl.notice({
    title: "Success!",
    message: (Alerts.messages[code] || ""),
    size: "large",
    fixed: true,
    duration: 30000,
  });
};

Alerts.error_for_code = function (error_code) {
  Alerts.clear();

  $.growl.error({
    message: (Alerts.messages[error_code] || "Unexpected error!  Please retry."),
    size: "large",
    fixed: true,
    duration: 30000,
  });
};


Vue.component('modal', {
  template: `
<div class="modal" tabindex="-1" role="dialog" aria-modal="true" ref="modal" :aria-label="headerText">
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
  data: function() {
    return {
      headerText: "",
    }
  },
  methods: {
    open: function(callback) {
      var self = this;

      if (callback) {
        $(this.$refs.modal).on('shown.bs.modal', function() {
          callback(self.$refs.modal);
        })
      }

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

    this.headerText = this.$slots.header[0].text;
  }
});

Vue.component('seat-assignment-widget', {
  template: `
<div>
    <div v-if="isStudent">
      <p>{{studentInstruction}}</p>
    </div>
    <span class="seat-assignment-widget">
      <div v-if="isStudent && isEditable && !!editableUntil" class="alert alert-warning">
        Note: You have {{timeLeftToEdit}} to make further edits to your seat number.
      </div>
      <template v-if="editing || seat">
        <label :for="inputId" class="sr-only">
          Seat assignment for {{netid}}
        </label>
        <input
          :id="inputId"
          type="text" 
          v-model="inputValue"
          ref="input"
          :class="'form-control ' + inputCSSClasses"
          :readonly="!editing"
          v-on:keydown.enter="editOrSave()"
          v-on:keydown.esc="cancel()"
          :required="isStudent"
          v-bind:style="{width: inputWidth}"
          maxlength="20"
          :aria-label="'Seat assignment for ' + studentName + ' (' + netid + ')'"
          :aria-invalid="'' + hasError"
        />
        <template v-if="isEditable && editing">
          <button class="btn-primary" @click="save()" :disabled="waitingOnSave">
            <i class="glyphicon glyphicon-ok" aria-hidden="true"></i> Save
          </button>
          <button @click="cancel()" :disabled="waitingOnSave">
            <i class="glyphicon glyphicon-ban-circle" aria-hidden="true"></i> Cancel
          </button>
        </template>
        <template v-else-if="isEditable">
          <button @click="edit()" :disabled="waitingOnSave || editDisabled" :aria-label="'Edit - ' + studentName + ' (' + netid + ')'">
            <i class="glyphicon glyphicon-pencil" aria-hidden="true"></i> Edit
          </button>
          <confirm-button
            v-if="!isStudent && seat !== null"
            :disabled="waitingOnSave || editDisabled"
            :action="function () { clear() }"
            :confirmMessage="'Clear seat assignment for ' + netid + '?'"
            :ariaLabel="'Clear seat number for ' + studentName + ' (' + netid + ')'"
          >
            <i class="glyphicon glyphicon-remove" aria-hidden="true"></i></i> Clear
          </confirm-button>
        </template>
      </template>
      <template v-else-if="isEditable">
        <button
            ref="enterSeatButton"
            @click="edit()"
            :disabled="editDisabled"
            :aria-label="'Enter seat number for ' + studentName + ' (' + netid + ')'"
          >
          <i class="glyphicon glyphicon-plus" aria-hidden="true"></i> {{labelText}}
        </button>
      </template>
    </span>
    <div v-if="isStudent">
      <p>{{studentNote}}</p>
    </div>
    <modal v-if="isStudent" ref="studentConfirmationModal">
      <template v-slot:header>Enter Seat Number</template>
      <template v-slot:body>
          <p>I agree to enter a seat number <em>only on my first day in the classroom</em> and as directed by my instructor. I understand that seats <strong>cannot</strong> be reserved prior to my first class meeting, and that such entries will not be honored and may be removed by my instructor.</p>
      </template>
      <template v-slot:footer>
        <button @click="studentConfirmSave()" class="pull-left btn-primary">Proceed</button>
        <button @click="hideStudentConfirmation(true)">Cancel</button>
      </template>
    </modal>
</div>
  `,
  data: function() {
    return {
      hasError: false,
      seatValueUponEditing: null,
      editing: false,
      inputValue: '',
      currentTime: 0,
      currentTimePoll: null,
      waitingOnSave: false,
      editDisabled: false,
      studentConfirmationReceived: false,
    };
  },
  props: ['seat', 'netid', 'meetingId', 'groupId', 'sectionId', 'isStudent', 'editableUntil', 'studentName', 'groupName'],
  computed: {
      labelText: function() {
        if (this.isStudent) {
          return "Enter Seat Number";
        } else {
          return "Enter Seat Assignment"
        }
      },
      inputWidth: function() {
        return Math.max(this.inputValue.length, 5) + 'em';
      },
      inputId: function() {
          return [this.meetingId, this.netid].join('__');
      },
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
      cleanSeatValue: function() {
        if (this.seat == null) {
              return '';
          } else {
              return '' + this.seat;
          }
      },
      isEditable: function() {
          if (!this.isStudent) {
              return true;
          }

          if (this.editableUntil === null) {
            // no seat value set so editable
            return true;
          }

          if (this.editableUntil === 0) {
            // seat was set by instructor
            return false;
          }

          // check the edit window
          return this.currentTime < this.editableUntil;
      },
      timeLeftToEdit: function() {
        var ms = this.editableUntil - this.currentTime;
        if (ms <= 0) {
          return 'no time';
        }

        var sec = parseInt(ms / 1000);
        if (sec <= 60) {
          return sec + " seconds"
        }

        var min = parseInt(sec / 60);
        return min + " minutes " + (sec % 60) + " seconds";
      },
      studentInstruction: function() {
        if (this.isEditable) {
          return this.studentName + ", enter your seat number here:";
        } else {
          return this.studentName + ", your seat number is:";
        }
      },
      studentNote: function() {
        if (this.isEditable) {
          return "Note: Only enter your seat number once you are in class and have chosen your seat.";
        } else {
          return "Note: If you need to change your seat number, contact your instructor.";
        }
      },
  },
  watch: {
    seat: function(a, b) {
      if (this.isStudent) {
        this.resetSeatValue();
        this.editing = false;
        return;
      }

      if (!this.editing) {
        this.resetSeatValue();
        if (a !== b) {
          this.highlightInput();
        }
      }
    },
  },
  methods: {
    studentConfirmSave: function() {
      this.studentConfirmationReceived = true;
      this.save();
      this.hideStudentConfirmation();
    },
    showStudentConfirmation: function() {
      this.$refs.studentConfirmationModal.open();
    },
    hideStudentConfirmation: function(cancelled) {
      this.$refs.studentConfirmationModal.close();
      if (cancelled) {
        this.cancel();
      }
    },
    resetSeatValue: function() {
      this.inputValue = this.cleanSeatValue;
    },
    cancel: function() {
      this.hasError = false;
      this.editing = false;

      SeatToolEventBus.$emit("done-editing", this._uid);

      this.resetSeatValue();
      this.focusInput();
    },
    editOrSave: function() {
      if (!this.isEditable) {
          return;
      }

      if (this.editing) {
        this.save();
      } else {
        this.edit()
      }
    },
    clear: function() {
      if (!this.isEditable) {
          return;
      }
      this.inputValue = '';
      this.save();
    },
    edit: function() {
      if (!this.isEditable) {
          return;
      }

      SeatToolEventBus.$emit("editing", this._uid, {
        studentName: this.studentName,
        netid: this.netid,
        group: this.groupName,
      });

      this.editing = true;
      this.seatValueUponEditing = this.seat;
      this.selectInput();
    },
    focusInput: function() {
      var self = this;
      self.$nextTick(function() {
        if (self.seat) {
          self.$refs.input.focus();
        } else {
          self.$refs.enterSeatButton.focus();
        }
      });
    },
    highlightInput: function() {
      var self = this;
      self.$nextTick(function() {
        if (self.$refs.input) {
          var elt = $(self.$refs.input);
          elt.animate({
            backgroundColor: "#90EE90",
          }, 400, function () {
            elt.animate({
              backgroundColor: "rgba(0, 0, 0, 0)",
            }, 1000)
          });
        }
      });
    },
    selectInput: function() {
      var self = this;
      self.$nextTick(function() {
        self.$refs.input.select();
      });
    },
    markHasError: function() {
      this.hasError = true;
    },
    clearHasError: function() {
      this.hasError = false;
    },
    save: function() {
      if (!this.isEditable) {
          return;
      }

      if (this.waitingOnSave) {
          return;
      }

      if (this.isStudent && !this.seat && !this.studentConfirmationReceived) {
        this.showStudentConfirmation();
        return;
      }

      var self = this;
      self.clearHasError();
      self.waitingOnSave = true;

      $.ajax({
        url: this.baseurl + "/seat-assignment",
        type: 'post',
        dataType: 'json',
        data: {
          sectionId: self.sectionId,
          groupId: self.groupId,
          meetingId: self.meetingId,
          netid: self.netid,
          seat: self.inputValue,
          currentSeat: self.seatValueUponEditing,
        },
        success: function(json) {
          if (json.error) {
            self.markHasError();
            self.selectInput();

            Alerts.error_for_code(json.error_code);
          } else {
            self.editing = false;
            SeatToolEventBus.$emit("done-editing", this._uid);

            self.seatValueUponEditing = null;
            self.focusInput();
            self.$emit("splat");

            Alerts.clear();
          }
        },
        complete: function() {
            self.waitingOnSave = false;
        },
      });
    }
  },
  beforeDestroy: function() {
    if (this.currentTimePoll) {
      clearInterval(this.currentTimePoll);
    }
  },
  mounted: function() {
    var self = this;

    self.inputValue = self.cleanSeatValue;

    if (self.isStudent) {
      self.currentTime = new Date().getTime();
      self.currentTimePoll = setInterval(function() {
        self.currentTime = new Date().getTime();
      }, 1000);
    }

    SeatToolEventBus.$on('editing', function(componentUid) {
      if (self._uid === componentUid) {
        // nothing
      } else {
        if (self.editing) {
          self.cancel();
        }
        self.editDisabled = true;
      }
    });

    SeatToolEventBus.$on('done-editing', function(componentUid) {
      if (self._uid != componentUid) {
        self.editDisabled = false;
      }
    });
  },
});

Vue.component('split-action', {
  template: `
<div>
  <button @click="openModal()">Create Section Cohorts</button>
  <contextual-help
    feature="Create Section Cohorts"
    helpText="Divides your section into two, three, or four cohorts, for alternating in-person attendance. Consult with your school or department leadership if you are unsure if you should create cohorts for this section.">
  </contextual-help>
  <modal ref="splitModal">
    <template v-slot:header>Create Section Cohorts</template>
    <template v-slot:body>
      <div v-if="hasSeatAssignments" class="alert alert-danger">
        <b>WARNING:</b> One or more members of this section has a seat assignment.  Any existing seat assignments will be cleared.
      </div>
      <div>
        <p>Break down your section into cohorts, which can be used to create multiple seating charts for the same room
           (e.g., if different cohorts will alternate in-class attendance by week).</p>
        <p>Section membership will be randomly distributed among the cohorts according to the rules defined below.</p>
        <label for="numberofgroups">Number of cohorts to create:</label>
        <contextual-help
          feature="Number of cohorts to create"
          helpText="Divide your section into two, three, or four cohorts, for alternating in-person attendance. Consult with your school or department leadership if you are unsure if you should create cohorts for this section.">
        </contextual-help>
        <select id="numberofgroups" v-model="numberOfGroups" class="form-control">
          <option v-for="i in (section.maxGroups - 1)" :value="i + 1">{{i + 1}}</option>
        </select>
      </div>
      <div v-if="hasBlended && hasRemoteUsers">
        <label for="selectionType">Remote student random cohort assignment:</label>
        <div class="well">
          <label>
            <input type="radio" v-model="selectionType" value="RANDOM"/> 
            Split remote students evenly among the created cohorts
          </label>
          <label>
            <input type="radio" v-model="selectionType" value="WEIGHTED"/> 
            Group remote students together in one cohort (if possible)
          </label>
          <p>Note: In-Person section members will be distributed randomly across the cohorts regardless of the options selected above.</p>
        </div>
      </div>
    </template>
    <template v-slot:footer>
      <button @click="performSplit()" class="pull-left btn-primary">Save</button>
      <button @click="closeModal()">Cancel</button>
    </template>
  </modal>
</div>
`,
  props: ['section'],
  data: function() {
    return {
      numberOfGroups: 2,
      selectionType: 'RANDOM',
    }
  },
  computed: {
    baseurl: function() {
      return this.$parent.baseurl;
    },
    hasBlended: function() {
      return this.section.hasBlended;
    },
    hasRemoteUsers: function() {
      for (var g = 0; g < this.section.groups.length; g++) {
        var group = this.section.groups[g];
        for (var m = 0; m < group.meetings.length; m++) {
          var meeting = group.meetings[m];
          for (var a = 0; a < meeting.seatAssignments.length; a++) {
            var seatAssignment = meeting.seatAssignments[a];

            if (seatAssignment.studentLocation === 'REMOTE') {
              return true;
            }
          }
        }
      }

      return false;
    },
    hasSeatAssignments: function() {
      for (var g = 0; g < this.section.groups.length; g++) {
        var group = this.section.groups[g];
        for (var m = 0; m < group.meetings.length; m++) {
          var meeting = group.meetings[m];
          for (var a = 0; a < meeting.seatAssignments.length; a++) {
            var seatAssignment = meeting.seatAssignments[a];

            if (seatAssignment.seat) {
              return true;
            }
          }
        }
      }

      return false;
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
      var self = this;

      $.ajax({
        url: this.baseurl + "/split-section",
        type: 'post',
        data: {
          sectionId: this.section.id,
          numberOfGroups: this.numberOfGroups,
          selectionType: this.selectionType,
        },
        success: function() {
          self.$emit('splat');
          self.closeModal();
        }
      })
    }
  }
});

Vue.component('group-meeting-summary', {
  template: `
<div class="pull-left">
  <div v-if="meeting.seatAssignments.length > 0">
    <span>{{meeting.seatAssignments.length}} member<template v-if="meeting.seatAssignments.length > 0">s</template></span>
    <span v-bind:style="{ marginLeft: '10px' }">{{locationSummary}}</span>
  </div>
</div>`,
  props: ['meeting'],
  computed: {
    locationSummary: function() {
      var counts = {};
      for (var i=0; i<this.meeting.seatAssignments.length; i++) {
        var studentLocation = this.meeting.seatAssignments[i].studentLocation;
        if (!counts[studentLocation]) {
          counts[studentLocation] = 0;
        }
        counts[studentLocation]++;
      }

      var result = [];

      $.each(counts, function(location, count) {
        result.push(count + ' ' + StudentLocationLabels[location]);
      });
      return '(' + result.join(', ') + ')';
    }
  }
});

Vue.component('group-meeting-entry', {
  template: `
<tr :class="entryCSSClass" :title="disabled ? disabledMessage : ''">
  <td v-if="SeatToolMode === 'sakai'">
    <div class="profile-pic">
      <img :src="'/direct/profile/' + assignment.netid + '/image/official?siteId=' + section.siteId"/>
    </div>
  </td>
  <th scope="row">{{assignment.displayName}} ({{assignment.netid}})</th>
  <td>
    <seat-assignment-widget
      ref="seatWidget"
      :seat="assignment.seat"
      :netid="assignment.netid"
      :studentName="assignment.displayName"
      :meetingId="meeting.id"
      :groupId="group.id"
      :sectionId="section.id"
      :isStudent="false"
      :groupName="groupName"
      v-on:splat="$emit('splat')">
    </seat-assignment-widget>
  </td>
  <td>
    {{labelForStudentLocation(assignment.studentLocation)}}
    <div v-if="!assignment.official">(Manually Added)</div>
  </td>
  <td>
    {{groupName}}
    <template v-if="isNotOnlyGroup">
      <a
        href="javascript:void(0)"
        @click="handleMove()"
        :aria-label="'Move User - ' + assignment.displayName + ' (' + assignment.netid + ')'"
      >Move</a>

      <template v-if="!assignment.official">
        <confirm-button
          :disabled="disabled || editing"
          :action="function () { removeUser(assignment) }"
          :confirmMessage="'Remove user ' + assignment.netid + ' from group ' + group.name + '?'"
          :ariaLabel="'Remove User - ' + assignment.displayName + ' (' + assignment.netid + ')'"
        >Remove User</confirm-button>
      </template>
    </template>
    <template v-else>
      <template v-if="!assignment.official">
        <confirm-button
          :disabled="disabled || editing"
          :action="function () { removeUser(assignment) }"
          :confirmMessage="'Remove user ' + assignment.netid + ' from group ' + section.shortName + '?'"
          :ariaLabel="'Remove User - ' + assignment.displayName + ' (' + assignment.netid + ')'"
        >Remove User</confirm-button>
      </template>
    </template>
  </td>
</tr>
`,
  props: ['assignment', 'meeting', 'group', 'section', 'isNotOnlyGroup', 'openMoveModal'],
  data: function() {
    return {
      disabled: false,
      editing: false,
      disabledMessage: '',
    };
  },
  computed: {
    groupName: function() {
      if (this.isNotOnlyGroup) {
        return this.group.name;
      } else {
        return this.section.shortName;
      }
    },
    baseurl: function() {
        return this.$parent.baseurl;
    },
    entryCSSClass: function() {
      if (this.disabled) {
        return 'seat-entry-disabled';
      } else if (this.editing) {
        return 'seat-entry-editing';
      } else {
        return 'seat-entry-enabled';
      }
    },
  },
  methods: {
    handleMove: function() {
      if (!this.disabled && !this.editing) {
        this.openMoveModal(this.assignment);
      }
    },
    labelForStudentLocation: function(studentLocation) {
      return StudentLocationLabels[studentLocation];
    },
    removeUser: function(assignment) {
      var self = this;

      $.ajax({
        url: self.baseurl + "/remove-group-user",
        type: 'post',
        data: {
          sectionId: self.section.id,
          groupId: self.group.id,
          netid: assignment.netid,
        },
        success: function() {
          self.$emit('splat');
        }
      });
    },
  },
  mounted: function() {
    var self = this;

    SeatToolEventBus.$on("done-editing", function(seatWidgetUid) {
      self.disabled = false;
      self.editing = false;
    });

    SeatToolEventBus.$on("editing", function(seatWidgetUid, data) {
      if (self.$refs.seatWidget._uid === seatWidgetUid) {
        self.editing = true;
      } else if (self.$refs.seatWidget._uid !== seatWidgetUid) {
        self.disabled = true;
        self.disabledMessage = "You are already editing the seat assignment for " + data.studentName + " (" + data.netid + ") in " + data.group;
      }
    });
  }
});

Vue.component('group-meeting', {
  template: `
<div>
  <div v-bind:style="{clear: 'both'}">
    <group-meeting-summary :meeting="meeting"></group-meeting-summary>
    <label class="pull-right">Sort by: 
      <select v-model="sortBy">
        <option value="STUDENT_LOCATION">Student Location</option>
        <option value="NETID">NetID</option>
      </select>
    </label>
  </div>
  <table class="seat-table seat-assignment-listing">
    <thead>
      <tr>
        <th v-if="SeatToolMode === 'sakai'" scope="col">Picture</th>
        <th scope="col">Name (NetID)</th>
        <th scope="col">Seat<br/>Assignment</th>
        <th scope="col">Student Location</th>
        <th scope="col">
          <template v-if="$parent.isNotOnlyGroup">
            Section Cohort
          </template>
          <template v-else>
            Section
          </template>
        </th>
      </tr>
    </thead>
    <tbody>
      <tr v-if="sortedSeatAssignments.length === 0">
        <td colspan="5">
          This cohort has no members.
        </td>
      </tr>
      <template v-for="assignment in sortedSeatAssignments">
        <group-meeting-entry
          :assignment="assignment"
          :key="assignment.netid + '__' + group.id"
          :group="group"
          :section="section"
          :meeting="meeting"
          v-on:splat="$emit('splat')"
          :isNotOnlyGroup="isNotOnlyGroup"
          :openMoveModal="openMoveModal"
        ></group-meeting-entry>
      </template>
    </tbody>
  </table>
  <modal v-if="assignmentToBeMoved" ref="moveModal">
    <template v-slot:header>Move Member ({{assignmentToBeMoved.displayName}})</template>
    <template v-slot:body>
      <div>

        <div v-if="assignmentToBeMoved.seat" class="alert alert-danger">
            <p>This member has an existing seat assignment. Moving to another cohort will clear their existing seat assignment.</p>
        </div>

        <p>Select the cohort to which you would like to move this section member.</p>

        <div class="form-group">
          <label :for="_uid + '_group'">Move to:</label>
          <select :id="_uid + '_group'" ref="moveToGroupSelect" type="text" class="form-control">
            <option v-for="otherGroup in otherGroups" :value="otherGroup.id">{{otherGroup.name}}</option>
          </select>
        </div>
     </div>
    </template>
    <template v-slot:footer>
      <button @click="move()" class="pull-left btn-primary">Move</button>
      <button @click="closeMoveModal()">Cancel</button>
    </template>
  </modal>
</div>
`,
  data: function() {
    return {
      assignmentToBeMoved: null,
      sortBy: 'STUDENT_LOCATION',
    };
  },
  props: ['section', 'group', 'meeting'],
  computed: {
    isNotOnlyGroup: function() {
      return this.$parent.isNotOnlyGroup;
    },
    baseurl: function() {
        return this.$parent.baseurl;
    },
    sortedSeatAssignments: function() {
      var self = this;

      return self.meeting.seatAssignments.sort(function(a, b) {
        if (self.sortBy === 'NETID') {
          return (a.netid < b.netid) ? -1 : 1;

        } else if (self.sortBy === 'SEAT') {
          if (a.seat === null) {
            return 1;
          } else if (b.seat === null) {
            return -1;
          } else {
            return (a.seat < b.seat) ? -1 : 1;
          }

        } else {
          if (a.studentLocation === b.studentLocation) {
            if (a.lastName === b.lastName) {
              return (a.netid < b.netid) ? -1 : 1;
            } else {
              return (a.lastName < b.lastName) ? -1 : 1;
            }
          } else {
            return (StudentLocationSortOrder[a.studentLocation] < StudentLocationSortOrder[b.studentLocation]) ? -1 : 1;
          }
        }
      })
    },
    otherGroups: function() {
      var otherGroups = [];

      for (var i=0; i<this.section.groups.length; i++) {
        var otherGroup = this.section.groups[i];
        if (this.group.id !== otherGroup.id) {
          otherGroups.push(otherGroup);
        }
      }

      return otherGroups.sort(function(a, b) {
        return a.name < b.name ? -1 : 1;
      });
    },
  },
  methods: {
    openMoveModal: function(assignment) {
      var self = this;

      self.assignmentToBeMoved = assignment;
      self.$nextTick(function() {
        self.$refs.moveModal.open();
      });
    },
    move: function() {
      var self = this;

      $.ajax({
        url: this.baseurl + "transfer-groups",
        type: 'post',
        data: {
          sectionId: self.section.id,
          fromGroupId: self.group.id,
          toGroupId: self.$refs.moveToGroupSelect.value,
          netid: self.assignmentToBeMoved.netid,
        },
        success: function() {
          self.closeMoveModal();
          self.$emit('splat');
        },
      });
    },
    closeMoveModal: function() {
      if (this.$refs.moveModal) {
        this.$refs.moveModal.close();
      }
    },
  }
});

Vue.component('input-with-char-count', {
  template: `
    <div style="text-align: right">
      <input :id="id" ref="textInput" type="text" class="form-control" v-on="$listeners" v-model="value" v-on:keyup="adjustCount()" :maxlength="chars" />
      <small><span>{{remaining}}</span> characters remaining</small>
    </div>
  `,
  props: ['id', 'chars'],
  data: function() {
    return {
      value: '',
      remaining: this.chars,
    }
  },
  methods: {
    setValue: function (s) {
      this.value = s;
      this.$nextTick(function() {
        this.adjustCount();
      });
    },
    getValue: function() {
      return this.value;
    },
    adjustCount: function() {
      this.remaining = Math.max(this.chars - this.$refs.textInput.value.length, 0);
    }
  }
});

Vue.component('email-cohort', {
    template: `
        <div>
            <modal ref="emailModal">
                <template v-slot:header>Email cohort: {{group.name}}</template>
                <template v-slot:body>
                    <p>Send a message to this cohort. You and other instructors, course site admins and teaching assistants in the site will be cc'd on the email.</p>
                    <div class="form-group">
                        <label :for="_uid + '_subject'">Subject:</label>
                        <input type="text" ref="emailSubject" class="form-control" :id="_uid + '_subject'" name="subject" />
                        <br />
                        <label :for="_uid + '_body'">Message body:</label>
                        <textarea :id="_uid + '_body'" ref="emailBody" name="body" class="form-control seats-email-body"></textarea>
                    </div>
                </template>
                <template v-slot:footer>
                    <button @click="sendEmail()" class="pull-left btn-primary"><i class="fa fa-envelope"></i> Send</button>
                    <button @click="closeEmailModal()">Cancel</button>
                </template>
            </modal>

            <button @click="showModal()" :class="htmlClass"><i class="fa fa-envelope"></i> Send Message to Cohort</button>
        </div>`,
    props: ['htmlClass', 'section', 'group'],
    methods: {
        showModal: function() {
            var self = this;

            self.$refs.emailSubject.value = '';
            self.$refs.emailBody.value = '';

            self.$refs.emailModal.open(function () {
                $(self.$refs.emailModal.$el).find('.form-control').first().focus();
            });
        },
        sendEmail: function() {
            var self = this;

            $.ajax({
                url: self.baseurl + "email-group",
                type: 'post',
                data: {
                    sectionId: self.section.id,
                    groupId: self.group.id,
                    subject: self.$refs.emailSubject.value,
                    body: self.$refs.emailBody.value,
                },
                success: function() {
                    Alerts.success("EMAIL_SENT");
                    self.closeEmailModal();
                },
            });

        },
        closeEmailModal: function() {
            this.$refs.emailModal.close();
        },
    },
    computed: {
        baseurl: function() {
            return this.$parent.baseurl;
        },
    }
});

Vue.component('confirm-button', {
  template: `
    <span>
      <modal ref="confirmModal">
        <template v-slot:header>Please Confirm</template>
        <template v-slot:body>
          <p>{{confirmMessage}}</p>
        </template>
        <template v-slot:footer>
          <button @click="doit()" class="pull-left btn-primary" style="background-color: #da4f49 !important; color: #fff !important"><slot></slot></button>
          <button @click="cancel()">Cancel</button>
        </template>
      </modal>

      <button
        :disabled="!!disabled"
        @click="showModal()"
        :aria-label="ariaLabel"
      >
        <slot></slot>
      </button>
    </span>
  `,
  props: ['action', 'confirmMessage', 'disabled', 'ariaLabel'],
  methods: {
    showModal: function() {
      this.$refs.confirmModal.open();
    },
    doit: function() {
      this.action();
      this.$refs.confirmModal.close();
    },
    cancel: function() {
      this.$refs.confirmModal.close();
    }
  }
});

Vue.component('contextual-help', {
  template: `
    <a
      href="javascript:void(0);"
      data-toggle="popover"
      data-trigger="focus"
      :data-content="helpText"
      :aria-label="helpText"
      ref="popover"
    >
      <i class="fa fa-question-circle" aria-hidden="true"></i>
      <span class="sr-only">Help on {{feature}}</span>
    </a>
`,
  props: ['feature', 'helpText'],
  mounted: function() {
    $(this.$refs.popover).popover();
  }
});


Vue.component('section-group', {
  template: `
<div>
  <hr v-if="isNotFirstGroup" />
  <template v-if="isNotOnlyGroup">
    <h2>{{group.name}} {{groupLabel}}</h2>
    <p>{{section.name}} <section-roster-summary :section="section"></section-roster-summary></p>
  </template>
  <div v-if="section.split" class="seat-section-description">
    <template v-if="group.description">
      <p>{{group.description}} <a href="javascript:void(0)" @click="showDescriptionModal()">Edit</a></p>
    </template>
    <template v-else>
      <a href="javascript:void(0);" @click="showDescriptionModal()">Add Description (optional)</a>
    </template>
  </div>
  <modal ref="descriptionModal">
    <template v-slot:header>Add Cohort Description {{group.name}}</template>
    <template v-slot:body>
      <div>
        <p>Add a short description for your cohort, which will display to students.</p>

        <div class="form-group">
          <label :for="_uid + '_description'">Description text:</label>
          <input-with-char-count v-on:keydown.enter="saveDescription()" chars=200 :id="_uid + '_description'" ref="descriptionInput" />
        </div>
     </div>
    </template>
    <template v-slot:footer>
      <button @click="saveDescription()" class="pull-left btn-primary">Save</button>
      <button @click="closeDescriptionModal()">Cancel</button>
    </template>
  </modal>
  <email-cohort v-if="section.split && !group.isGroupEmpty" htmlClass="email-cohort-btn pull-right" :group="group" :section="section" />
  <template v-if="isNotOnlyGroup && group.isGroupEmpty">
    <confirm-button :action="deleteGroup" :confirmMessage="'Really remove cohort ' + group.name + '?'">Remove Cohort</confirm-button>
  </template>
  <template v-for="meeting in group.meetings">
    <group-meeting
      :key="meeting.id"
      :group="group"
      :section="section"
      :meeting="meeting"
      v-on:splat="$emit('splat')"
    >
    </group-meeting>
  </template>
  <button v-if="SeatToolMode === 'sakai'" @click="addAdhocMembers()">Add additional site member(s)</button>
  <contextual-help
    feature="Add additional site member(s)"
    helpText="Additional site members are students, instructors, TAs, or course site administrators within the course site who are not part of the official student roster(s) from Albert. This includes individuals manually added to the course site in the Settings tool.">
  </contextual-help>
  <modal ref="membersModal">
    <template v-slot:header>Add additional site member(s) {{group.name}}</template>
    <template v-slot:body>
      <div>
        <p>Select manually-added, non-official site members in this site to add to this cohort. More information on <a target="_blank" href="https://www.nyu.edu/servicelink/041212911320118">manually adding members to your course site</a>.</p>

        <table class="seat-table members-for-add-listing">
          <thead>
            <tr>
              <th scope="col" class="sr-only">Checkbox</th>
              <th scope="col">Participant</th>
              <th scope="col">Role</th>
            </tr>
          </thead>
          <tbody>
            <template v-if="membersForAdd === null">
              <tr><td colspan="3">Loading...</td></tr>
            </template>
            <template v-else>
              <tr v-for="user in membersForAdd" :key="user.netid">
                <td style="padding: 0 0 0 1em; text-align: right;"><input style="margin: 0" :id="'checkbox_' + this._uid + '_' + user.netid" type="checkbox" v-model="selectedMembers" :value="user.netid" /></td>
                <th scope="row"><label :for="'checkbox_' + this._uid + '_' + user.netid">{{user.displayName}} ({{user.netid}})</label></th>
                <td>{{user.role}}</td>
              </tr>
            </template>
          </tbody>
        </table>
     </div>

    </template>
    <template v-slot:footer>
      <button @click="saveUsers()" class="pull-left primary">Add Member(s)</button>
      <button @click="closeModal()">Cancel</button>
    </template>
  </modal>
</div>`,
  props: ['section', 'group'],
  data: function() {
    return {
      selectedMembers: [],
      membersForAdd: [],
    }
  },
  methods: {
    deleteGroup: function() {
      var self = this;
      $.ajax({
        url: self.baseurl + "delete-group",
        type: 'post',
        data: {
          sectionId: self.section.id,
          groupId: self.group.id,
        },
        success: function() {
          self.$emit('splat');
        },
      });
    },
    showDescriptionModal: function() {
      var self = this;

      this.$refs.descriptionModal.open(function () {
        $(self.$refs.descriptionModal.$el).find('.form-control').first().focus();
      });

      this.$refs.descriptionInput.setValue(this.group.description || '');
    },
    saveDescription: function() {
      var self = this;

      $.ajax({
        url: self.baseurl + "/save-group-description",
        type: 'post',
        data: {
          groupId: self.group.id,
          description: self.$refs.descriptionInput.getValue(),
        },
        dataType: 'json',
        success: function(json) {
          self.$refs.descriptionModal.close();
          self.$emit('splat');
        }
      });
    },
    closeDescriptionModal: function() {
      this.$refs.descriptionModal.close();
    },
    addAdhocMembers: function() {
      var self = this;

      self.selectedMembers = [];
      self.membersForAdd = null;

      this.$refs.membersModal.open(function (modalElt) {
        $.ajax({
          url: self.baseurl + "/available-site-members",
          type: 'get',
          data: {
            sectionId: self.section.id,
            groupId: self.group.id,
          },
          dataType: 'json',
          success: function(json) {
            self.membersForAdd = json.sort(function (a, b) { return a.netid.localeCompare(b.netid) });
          }
        })
      });
    },
    saveUsers: function() {
      var self = this;

      $.ajax({
        url: self.baseurl + "/add-group-users",
        type: 'post',
        data: {
          sectionId: self.section.id,
          groupId: self.group.id,
          selectedMembers: self.selectedMembers,
        },
        success: function() {
          self.$emit('splat');
          self.closeModal();
          Alerts.success("MEMBER_ADDED");
        }
      });
    },
    closeModal: function() {
      this.$refs.membersModal.close();
    },
  },
  computed: {
    baseurl: function() {
        return this.$parent.baseurl;
    },
    isNotOnlyGroup: function() {
      return this.section.groups.length > 1;
    },
    isNotFirstGroup: function() {
      return this.section.groups.indexOf(this.group) > 0;
    },
    groupLabel: function() {
      return "(" + (this.section.groups.indexOf(this.group) + 1) + " of " + this.section.groups.length +  ")";
    }
  },
});

Vue.component('section-roster-summary', {
  template: `
<div>
  <small v-if="hasCrosslistedRosters" class="text-muted">({{primaryRoster}}, {{croslistedRosters.join(', ')}})</small>
</div>
`,
  props: ['section'],
  computed: {
    hasCrosslistedRosters: function() {
      if (this.section == null) {
        return false;
      } else {
        return this.section.rosters.length > 1;
      }
    },
    primaryRoster: function() {
      if (this.section == null) {
        return '';
      } else {
        return this.section.rosters.filter(function(roster) {
          return roster.primary;
        })[0].id;
      }
    },
    croslistedRosters: function() {
      if (this.section == null) {
        return [];
      } else {
        return this.section.rosters.filter(function(roster) {
          return !roster.primary;
        }).map(function(roster) {
          return roster.id;
        });
      }
    },
  },
});

Vue.component('section-table', {
  template: `
    <div>
      <template v-if="section">
          <h2 v-if="section.groups.length === 1">
            {{section.name}} <section-roster-summary :section="section"></section-roster-summary>
          </h2>
          <split-action
            v-if="section.hasBlended && !section.split"
            :section="section"
            v-on:splat="resetPolling()">
          </split-action>
          <template v-for="group in sortedGroups">
            <section-group :key="group.id" :group="group" :section="section" v-on:splat="resetPolling()"></section-group>
          </template>
          <template v-if="section.split && section.groups.length < section.maxGroups">
            <hr/>
            <button @click="addGroup()">Add Another Section Cohort</button>
          </template>
      </template>
    </div>
  `,

  data: function() {
    return {
        section: null,
        pollInterval: null,
        pollDelay: 5000,
    };
  },
  props: ['sectionId'],
  watch: {
    sectionId: function(a, b) {
      if (a != b) {
        this.resetPolling();
      }
    },
  },
  beforeDestroy: function() {
    this.cancelPolling();
  },
  computed: {
      baseurl: function() {
          return this.$parent.baseurl;
      },
      sortedGroups: function() {
        if (this.section == null) {
          return [];
        } else {
          return this.section.groups.sort(function(a, b) {
            return (a.name < b.name) ? -1 : 1;
          })
        }
      },
  },
  methods: {
      addGroup: function() {
        var self = this;
        $.ajax({
          url: self.baseurl + "add-group",
          type: 'post',
          data: {
            sectionId: self.section.id,
          },
          success: function() {
            self.resetPolling();
          },
        });
      },
      fetchData: function() {
          var self = this;

          $.ajax({
              url: self.baseurl + 'section',
              data: {
                  sectionId: self.sectionId,
              },
              type: 'get',
              dataType: 'json',
              success: function (json, _status, request) {
                  var newDelay = parseInt(request.getResponseHeader("X-Poll-Frequency"));
                  if (!isNaN(newDelay)) {
                      if (newDelay < 1000) {
                          newDelay = 1000;
                      }

                      self.pollDelay = newDelay;
                  }

                  self.section = json;
              }
          });
      },
      cancelPolling: function() {
        var self = this;
        if (self.pollInterval) {
          clearTimeout(self.pollInterval);
        }
        
      },
      resetPolling: function() {
        var self = this;

        self.cancelPolling();

        (function nextTick() {
          self.pollInterval = setTimeout(nextTick, self.pollDelay);

          self.fetchData();
        })();
      },
  },
  mounted: function() {
    this.resetPolling();
  }
});


Vue.component('section-selector', {
  template: `
<div>
  <label for="sectionSelector">
    Viewing seat assignments for:
  </label>
  <select v-model="selectedSectionId" id="sectionSelector">
    <option value="">Select a class section or cohort</option>
    <option v-for="section in sections" :value="section.id">
      {{section.name}}
      <template v-if="section.groupCount > 1">
        ({{section.groupCount}} cohorts)
      </template>
    </option>
  </select>
</div>
`,
  props: ['sections'],
  data: function() {
    return {
      selectedSectionId: '',
    };
  },
  watch: {
    selectedSectionId: function() {
      this.$emit('selectSection', this.selectedSectionId == '' ? null : this.selectedSectionId);
    },
  },
  methods: {
  },
});


Vue.component('instructor-table', {
  template: `
      <div>
          <button v-if="selectedSectionId" @click="print()" class="pull-right">Print</button>
          <template v-if="sections.length > 0">
              <section-selector
                  v-if="sections.length > 1"
                  ref="sectionSelector"
                  :sections="sections"
                  v-on:selectSection="handleSectionSelect">
              </section-selector>
              <p :style="{marginTop: '20px'}" v-if="fetched && sections.length > 1">To get started, see the <a target="_blank" href="https://drive.google.com/file/d/1uN1ihjlbvmjb8EOMvG4ZJVCh4GQweRrr/view?usp=sharing">Quick-Start guide</a> or review the <a target="_blank" href="http://www.nyu.edu/servicelink/KB0018303">instructor Knowledgebase article</a>.</p>

              <hr v-if="sections.length > 1" />
              <template v-if="selectedSectionId">
                  <section-table :sectionId="selectedSectionId"></section-table>
              </template>
              <template v-else>
                <center>
                  <img src="/seats-tool/images/splash.png" alt="Select a section instructional art"/>
                  <p>Select a section / cohort from the dropdown menu above.</p>
                </center>
              </template>
          </template>
          <template v-else-if="fetched">
            <p v-if="isSakai">This tool is yet to be provisioned. Please try again in a moment.</p>
            <p v-else>This site is not eligible to record seating assignments. The Seating Assignments tool is available for in-person or blended course sections only. If you believe that you are seeing this message in error, contact the IT Service Desk at <a href="mailto:askit@nyu.edu">askit@nyu.edu</a>.</p>
          </template>
      </div>

  `,
  data: function() {
    return {
        sections: [],
        selectedSectionId: null,
        fetched: false,
    };
  },
  props: ['baseurl'],
  methods: {
      print: function() {
        window.print();
      },
      fetchData: function() {
          var self = this;

          $.ajax({
              url: self.baseurl + 'sections',
              type: 'get',
              dataType: 'json',
              success: function (json) {
                  self.fetched = true;
                  self.sections = json;
                  if (self.sections.length === 1) {
                    self.selectedSectionId = self.sections[0].id;
                  }
              }
          });
      },
      handleSectionSelect: function(sectionId) {
        this.selectedSectionId = sectionId;
      },
  },
  computed: {
    isSakai: function() {
      return Vue.prototype.SeatToolMode === 'sakai';
    },
  },
  mounted: function() {
      this.fetchData();
  },
});


Vue.component('student-home', {
  template: `
    <div>
        <template v-if="fetched">
            <div v-if="roleSwap" class="messageInstruction">
              Students will be able to enter and view their seat number from here. For more info, see the following Kbase article: <a href="http://www.nyu.edu/servicelink/KB0018304" target="_blank">Entering and viewing your seating assignments (student article)</a>.
            </div>
            <div v-if="meetings.length === 0" class="alertMessage">
                You are currently not a member of any section or cohort. Please contact your instructor to be added.
            </div>
            <div v-for="(meeting, index) in sortedMeetings" :key="meeting.meetingId">
                <h2>{{meeting.groupName}}</h2>
                <p>{{meeting.sectionName}}<p>
                <p class="seat-section-description">{{meeting.groupDescription}}</p>
                <seat-assignment-widget
                  :seat="meeting.seat"
                  :netid="meeting.netid"
                  :studentName="meeting.studentName"
                  :meetingId="meeting.meetingId"
                  :groupId="meeting.groupId"
                  :sectionId="meeting.sectionId"
                  :isStudent="true"
                  :editableUntil="meeting.editableUntil"
                  v-on:splat="resetPolling()">
                </seat-assignment-widget>
                <hr v-if="index < sortedMeetings.length - 1"/>
            </div>
        </template>
        <div class="messageInstruction">
            <i style="color: #265b81; font-size: 150%; float: left; padding: 0 1em 0 0; line-height: 2em;" aria-hidden="true" class="fa fa-universal-access"></i>

            <p>Please note that instructors may need to reassign seats to accommodate those with disabilities.<br>
            Your cooperation and support is much appreciated.</p>
        </div>
    </div>
  `,
  data: function() {
    return {
        meetings: [],
        fetched: false,
        pollInterval: null,
        pollDelay: 20000,
        roleSwap: false,
    };
  },
  props: ['baseurl'],
  methods: {
      fetchData: function() {
          var self = this;

          $.ajax({
              url: self.baseurl + 'student-meetings',
              type: 'get',
              dataType: 'json',
              success: function (json, _status, request) {
                  var newDelay = parseInt(request.getResponseHeader("X-Poll-Frequency"));
                  if (!isNaN(newDelay)) {
                      if (newDelay < 1000) {
                          newDelay = 1000;
                      }

                      self.pollDelay = newDelay;
                  }

                  self.fetched = true;
                  self.meetings = json.meetings;
                  self.roleSwap = !!json.roleSwap;
              }
          });
      },
      handleSectionSelect: function(sectionId) {
        this.selectedSectionId = sectionId;
      },
      resetPolling: function() {
        var self = this;

        if (self.pollInterval) {
          clearTimeout(self.pollInterval);
        }

        (function nextTick() {
          self.pollInterval = setTimeout(nextTick, self.pollDelay);

          self.fetchData();
        })();
      }
  },
  computed: {
    sortedMeetings: function() {
      if (this.fetched) {
        return this.meetings.sort(function (a, b) { return a.groupName.localeCompare(b.groupName) });
      } else {
        return [];
      }
    },
  },
  beforeDestroy: function() {
    if (this.pollInterval) {
      clearTimeout(this.pollInterval);
    }
  },
  mounted: function() {
      var self = this;

      self.resetPolling();
  },
});
