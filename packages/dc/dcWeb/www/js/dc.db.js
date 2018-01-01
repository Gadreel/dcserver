// dc.pui.Loader.addExtraLibs( ['/js/dc.db.js'] );

if (!dc.db)
	dc.db = {};

dc.db.database = {
	Ping: function() {
		dc.comm.sendMessage({
			Service: 'dcDatabase',
			Feature: 'ExecuteProc',
			Op: 'dcPing'
		}, function(resp) {
			if (resp.Result > 0) {
				console.log('error: ' + resp.Message);
				return;
			}

			console.log('Reply: ' + resp.Body);
		});
	},
	Echo: function(text) {
		dc.comm.sendMessage({
			Service: 'dcDatabase',
			Feature: 'ExecuteProc',
			Op: 'dcEcho',
			Body: text
		}, function(resp) {
			if (resp.Result > 0) {
				console.log('error: ' + resp.Message);
				return;
			}

			console.log('Reply: ' + resp.Body);
		});
	},
	Encrypt: function(text) {
		dc.comm.sendMessage({
			Service: 'dcDatabase',
			Feature: 'ExecuteProc',
			Op: 'dcEncrypt',
			Body: {
				Value: text
			}
		}, function(resp) {
			if (resp.Result > 0) {
				console.log('error: ' + resp.Message);
				return;
			}

			console.log('Reply: ' + resp.Body.Value);
		});
	},
	Hash: function(text) {
		dc.comm.sendMessage({
			Service: 'dcDatabase',
			Feature: 'ExecuteProc',
			Op: 'dcHash',
			Body: {
				Value: text
			}
		}, function(resp) {
			if (resp.Result > 0) {
				console.log('error: ' + resp.Message);
				return;
			}

			console.log('Reply: ' + resp.Body.Value);
		});
	},
	Select: function(params, cb) {

		/*
		 dc.db.database.Select({
			Table: 'dcUser',
			Select: [
				{
					Field: 'Id'
				},
				{
					Field: 'dcUsername'
				},
				{
					Field: 'dcFirstName'
				},
				{
					Field: 'dcLastName'
				}
			],
			Where: {
				Expression: 'StartsWith',
				A: {
					Field: 'dcEmail'
				},
				B: {
					Value: 'andy'
				}
			}
		 })
		 *
		 *
		 *
		 dc.db.database.Select({
			Table: 'dcUser',
			Select: [
				{
					Field: 'Id'
				},
				{
					Field: 'dgaZipPrefix',
					Name: 'ZipPrefix'
				},
				{
					Field: 'dgaDisplayName',
					Name: 'DisplayName'
				},
				{
					Field: 'dgaIntro',
					Name: 'Intro'
				},
				{
					Field: 'dgaImageSource',
					Name: 'ImageSource'
				},
				{
					Field: 'dgaImageDiscreet',
					Name: 'ImageDiscreet'
				},
				{
					Field: 'dcmState',
					Name: 'State'
				},
				{
					Field: 'dgaVisibleToList',
					Name: 'VisibleToList'
				},
				{
					Field: 'dcAuthorizationTag',
					Name: 'Badges'
				}
			],
			Where: {
				Expression: 'And',
				Children: [
					{
						Expression: 'Equal',
						A: {
							Field: 'dgaAccountState'
						},
						B: {
							Value: 'Active'
						}
					},
					{
						Expression: 'Equal',
						A: {
							Field: 'dcConfirmed'
						},
						B: {
							Value: true
						}
					}
				]
			},
			Collector: {
				Field: 'dcAuthorizationTag',
				Values: [ 'ApprenticeCandidate' ]
			}
		 })

		<Request>
			<Field Name="Table" Type="dcTinyString" Required="True" />
			<Field Name="When" Type="dcTinyString" />
			<Field Name="Select">
				<List Type="dcdbSelectField" />
			</Field>
			<Field Name="Where" Type="dcdbWhereClause" />
			<Field Name="Collector">
				<Record>
					<Field Name="Func" Type="dcTinyString" />
					<!-- or -->
					<Field Name="Field" Type="dcTinyString" />
					<Field Name="SubId" Type="dcTinyString" />
					<Field Name="From" Type="Any" />
					<Field Name="To" Type="Any" />
					<Field Name="Values">
						<List Type="Any" />
					</Field>
					<Field Name="Extras" Type="AnyRecord" />
				</Record>
			</Field>
			<Field Name="Historical" Type="Boolean" />
		</Request>
		*/

		if (!params.Select)
			params.Select = [ ];  // select all

		dc.comm.sendMessage({
			Service: 'dcDatabase',
			Feature: 'ExecuteProc',
			Op: 'dcSelectDirect',
			Body: params
		}, function(resp) {
			if (resp.Result > 0) {
				console.log('error: ' + resp.Message);
				return;
			}

			if (cb)
				cb.callback(resp.Body);
			else
				console.table(resp.Body);
		});
	},
	Insert: function(params, cb) {

		/*
		 dc.db.database.Insert({
			Table: 'dcUser',
			Fields: {
				dcLocale: {
					Data: 'es',
					UpdateOnly: true
				},
				dcAuthorizationTag: {
					Admin: {
						Data: 'Admin'
					},
					Staff: {
						Data: 'Staff'
					}
				}
			}
		 })

		*/

		dc.comm.sendMessage({
			Service: 'dcDatabase',
			Feature: 'ExecuteProc',
			Op: 'dcInsertRecord',
			Body: params
		}, function(resp) {
			if (resp.Result > 0) {
				console.log('error: ' + resp.Message);
				return;
			}

			if (cb)
				cb.callback(resp.Body);
			else
				console.table(resp.Body);
		});
	},
	Update: function(params, cb) {

		/*
		 dc.db.database.Update({
			Table: 'dcUser',
			Id: 'xxx',
			Fields: {
				dcLocale: {
					Data: 'es',
					UpdateOnly: true
				},
				dcAuthorizationTag: {
					Admin: {
						Data: 'Admin'
					},
					Staff: {
						Data: 'Staff'
					}
				}
			}
		 })

		*/

		dc.comm.sendMessage({
			Service: 'dcDatabase',
			Feature: 'ExecuteProc',
			Op: 'dcUpdateRecord',
			Body: params
		}, function(resp) {
			if (resp.Result > 0) {
				console.log('error: ' + resp.Message);
				return;
			}

			if (cb)
				cb.callback(resp.Body);
			else
				console.table(resp.Body);
		});
	},
	Retire: function(table, id, cb) {
		dc.comm.sendMessage({
			Service: 'dcDatabase',
			Feature: 'ExecuteProc',
			Op: 'dcRetireRecord',
			Body: { Table: table, Id: id }
		}, function(resp) {
			if (resp.Result > 0) {
				console.log('error: ' + resp.Message);
				return;
			}

			if (cb)
				cb.callback();
		});
	}
}
