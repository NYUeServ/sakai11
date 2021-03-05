function hijackSetupCourseSites() {
    const $link = $PBJQ('#toolMenu a[title^="Set Up Course Sites"]');

    if ($link.length == 0) {
        return;
    }

    const sakaiTarget = $link.attr('href');
    $link.attr('href', 'javascript:void(0)');
    $link.on('click', function() {
        $(document.body).append($('#hijackCourseSiteSetupContentTemplate').html());
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