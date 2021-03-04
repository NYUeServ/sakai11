function hijackSetupCourseSites() {
    const $link = $PBJQ('#toolMenu a[title^="Set Up Course Sites"]');

    if ($link.length == 0) {
        return;
    }

    const sakaiTarget = $link.attr('href');
    $link.attr('href', 'javascript:void(0)');
    $link.on('click', function() {
        $(document.body).append('<div class="modal" tabindex="-1" role="dialog" id="nyuCourseSiteSetupModal"><div class="modal-dialog" role="document"><div class="modal-content"><div class="modal-header"><strong class="modal-title">Course Site Setup</strong><button type="button" class="close" data-dismiss="modal" aria-label="Close"><span aria-hidden="true">&times;</span></button></div><div class="modal-body"><p><a class="btn btn-primary nyu-course-site-setup-classes" href="javascript:void(0)">Create Course in NYU Classes</a></p><p><a class="btn btn-primary" href="https://brightspace.nyu.edu/">Create Course in NYU LMS (Brightspace)</a></p></div></div></div></div>');
        $('#nyuCourseSiteSetupModal')
            .on('hidden.bs.modal', function() {
                 $('#nyuCourseSiteSetupModal').remove();
            })
            .on('click', '.nyu-course-site-setup-classes', function(event) {
                event.preventDefault();
                window.location.href = sakaiTarget;
            });
        $('#nyuCourseSiteSetupModal').modal(); 
    });
};

hijackSetupCourseSites();